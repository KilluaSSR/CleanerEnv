mod abi;
mod guard;
mod handoff;
mod proto;
mod watch;

use std::ffi::CStr;
use std::os::raw::{c_int, c_void};
use std::panic::{catch_unwind, AssertUnwindSafe};

use jni_sys::JNIEnv;

use abi::{Api, ApiTable, AppSpecializeArgs, ModuleAbi};
use cleaner_core::consts;
use cleaner_core::rule::Rule;

const MOUNT_EXTERNAL_NONE: i32 = 0;

struct Module {
    api: Api,
    env: *mut JNIEnv,
    isolated: Option<String>,
    guard: Option<guard::PendingGuard>,
}

impl Module {
    fn pre_app_specialize(&mut self, args: &mut AppSpecializeArgs) {
        self.api.set_option(abi::OPTION_DLCLOSE_MODULE_LIBRARY);

        let uid = unsafe { *args.uid } as u32;
        let child_zygote = unsafe { args.is_child_zygote.as_ref() }.is_some_and(|flag| *flag != 0);
        if !is_regular_app(uid) || child_zygote {
            return;
        }
        let package = package_name(self.env, args);
        let nice_name = read_jstring(self.env, unsafe { *args.nice_name });
        if guard::is_media_provider(package.as_deref(), nice_name.as_deref()) {
            self.guard = guard::PendingGuard::prepare(&self.api, uid);
            return;
        }
        if unsafe { *args.mount_external } == MOUNT_EXTERNAL_NONE {
            return;
        }
        let Some(package) = package else {
            return;
        };

        let fd = self.api.connect_companion();
        if fd < 0 {
            return;
        }
        proto::set_receive_timeout(fd, 3000);
        if query_companion(fd, uid, &package) == proto::VERDICT_ISOLATE {
            self.isolated = Some(package);
        }
        unsafe { libc::close(fd) };
    }

    fn post_app_specialize(&mut self) {
        if let Some(pending) = self.guard.take() {
            pending.arm(self.env);
        }
        if let Some(package) = self.isolated.take() {
            if !handoff::request(&package) {
                cleaner_core::log_e!("{package} starts without its isolated storage view");
            }
        }
    }
}

fn query_companion(fd: c_int, uid: u32, package: &str) -> u8 {
    let bytes = package.as_bytes();
    let sent = proto::write_all(fd, &[proto::OP_ISOLATE])
        && proto::write_all(fd, &uid.to_le_bytes())
        && proto::write_all(fd, &(bytes.len() as u32).to_le_bytes())
        && proto::write_all(fd, bytes);
    if !sent {
        return proto::VERDICT_SKIP;
    }
    proto::read_u8(fd).unwrap_or(proto::VERDICT_SKIP)
}

fn is_regular_app(uid: u32) -> bool {
    let app_id = uid % consts::PER_USER_RANGE;
    (consts::FIRST_APPLICATION_UID..=consts::LAST_APPLICATION_UID).contains(&app_id)
}

fn package_name(env: *mut JNIEnv, args: &AppSpecializeArgs) -> Option<String> {
    let from_data_dir = read_jstring(env, unsafe { *args.app_data_dir })
        .and_then(|dir| dir.rsplit('/').next().map(String::from));
    let package = match from_data_dir {
        Some(package) if cleaner_core::rule::is_valid_package(&package) => package,
        _ => {
            let nice_name = read_jstring(env, unsafe { *args.nice_name })?;
            String::from(nice_name.split(':').next()?)
        }
    };
    cleaner_core::rule::is_valid_package(&package).then_some(package)
}

fn read_jstring(env: *mut JNIEnv, value: jni_sys::jstring) -> Option<String> {
    if env.is_null() || value.is_null() {
        return None;
    }
    unsafe {
        let get = (**env).GetStringUTFChars?;
        let release = (**env).ReleaseStringUTFChars?;
        let chars = get(env, value, std::ptr::null_mut());
        if chars.is_null() {
            return None;
        }
        let owned = CStr::from_ptr(chars).to_str().ok().map(String::from);
        release(env, value, chars);
        owned
    }
}

fn companion_main(client: c_int) {
    cleaner_core::state::write_heartbeat(target_abi());
    proto::set_receive_timeout(client, 3000);

    match proto::read_u8(client) {
        Some(proto::OP_ISOLATE) => serve_isolation(client),
        Some(proto::OP_MEDIA_GUARD) => guard::serve_media_guard(client),
        _ => {}
    }
}

fn serve_isolation(client: c_int) {
    let Some(uid) = proto::read_u32(client) else {
        return;
    };
    let Some(length) = proto::read_u32(client) else {
        return;
    };
    if length == 0 || length > proto::MAX_PACKAGE_LEN {
        let _ = proto::write_all(client, &[proto::VERDICT_SKIP]);
        return;
    }
    let mut buffer = vec![0u8; length as usize];
    if !proto::read_exact(client, &mut buffer) {
        return;
    }
    let Ok(package) = String::from_utf8(buffer) else {
        let _ = proto::write_all(client, &[proto::VERDICT_SKIP]);
        return;
    };

    let user_id = uid / consts::PER_USER_RANGE;
    let verdict = if Rule::load_enabled(user_id, &package).is_some() {
        handoff::ensure_listening();
        proto::VERDICT_ISOLATE
    } else {
        proto::VERDICT_SKIP
    };
    let _ = proto::write_all(client, &[verdict]);
}

fn target_abi() -> &'static str {
    if cfg!(target_arch = "aarch64") {
        "arm64-v8a"
    } else if cfg!(target_arch = "arm") {
        "armeabi-v7a"
    } else if cfg!(target_arch = "x86_64") {
        "x86_64"
    } else {
        "x86"
    }
}

unsafe extern "C" fn pre_app(this: *mut c_void, args: *mut AppSpecializeArgs) {
    if let (Some(module), Some(args)) = (this.cast::<Module>().as_mut(), args.as_mut()) {
        let _ = catch_unwind(AssertUnwindSafe(|| module.pre_app_specialize(args)));
    }
}

unsafe extern "C" fn post_app(this: *mut c_void, _args: *const AppSpecializeArgs) {
    if let Some(module) = this.cast::<Module>().as_mut() {
        let _ = catch_unwind(AssertUnwindSafe(|| module.post_app_specialize()));
    }
}

unsafe extern "C" fn pre_server(this: *mut c_void, _args: *mut c_void) {
    if let Some(module) = this.cast::<Module>().as_mut() {
        module.api.set_option(abi::OPTION_DLCLOSE_MODULE_LIBRARY);
    }
}

unsafe extern "C" fn post_server(_this: *mut c_void, _args: *const c_void) {}

unsafe fn register(table: *mut ApiTable, env: *mut JNIEnv) {
    let Some(register_module) = table.as_ref().and_then(|t| t.register_module) else {
        return;
    };
    let module = Box::into_raw(Box::new(Module { api: Api::new(table), env, isolated: None, guard: None }));
    let abi = Box::into_raw(Box::new(ModuleAbi {
        api_version: abi::ZYGISK_API_VERSION,
        impl_: module.cast(),
        pre_app_specialize: pre_app,
        post_app_specialize: post_app,
        pre_server_specialize: pre_server,
        post_server_specialize: post_server,
    }));
    if !register_module(table, abi) {
        drop(Box::from_raw(abi));
        drop(Box::from_raw(module));
    }
}

#[no_mangle]
pub unsafe extern "C" fn zygisk_module_entry(table: *mut ApiTable, env: *mut JNIEnv) {
    let _ = catch_unwind(AssertUnwindSafe(|| register(table, env)));
}

#[no_mangle]
pub unsafe extern "C" fn zygisk_companion_entry(client: c_int) {
    let _ = catch_unwind(AssertUnwindSafe(|| companion_main(client)));
}
