use std::collections::HashSet;
use std::ffi::{CStr, CString};
use std::fs::File;
use std::io::Read;
use std::os::raw::c_int;
use std::ptr;
use std::sync::Mutex;

use jni_sys::{jclass, jsize, jvalue, JNIEnv};

use crate::abi::Api;
use crate::proto;
use cleaner_core::{consts, fsmeta, policy};

const DEX_NAME: &CStr = c"media_guard.dex";
const MAX_DEX_LEN: u64 = 4 << 20;

const MEDIA_PACKAGES: [&str; 3] = [
    "com.android.providers.media.module",
    "com.google.android.providers.media.module",
    "com.android.providers.media",
];
const LEGACY_MEDIA_PROCESS: &str = "android.process.media";

const SETTLE_MS: u64 = 200;
const RESCAN_MS: c_int = 5_000;

pub fn is_media_provider(package: Option<&str>, nice_name: Option<&str>) -> bool {
    package.is_some_and(|p| MEDIA_PACKAGES.contains(&p)) || nice_name == Some(LEGACY_MEDIA_PROCESS)
}

pub struct PendingGuard {
    dex: Vec<u8>,
    policy_file: CString,
}

impl PendingGuard {
    pub fn prepare(api: &Api, uid: u32) -> Option<PendingGuard> {
        let policy_file = CString::new(policy::policy_file(uid / consts::PER_USER_RANGE)).ok()?;
        let fd = api.connect_companion();
        if fd < 0 {
            cleaner_core::log_e!("media guard: companion unavailable for uid {uid}");
            return None;
        }
        proto::set_receive_timeout(fd, 3000);
        let published = proto::write_all(fd, &[proto::OP_MEDIA_GUARD])
            && proto::write_all(fd, &uid.to_le_bytes())
            && proto::read_u8(fd) == Some(1);
        let dex = if published { receive_dex(fd) } else { None };
        unsafe { libc::close(fd) };
        if !published {
            cleaner_core::log_e!("media guard: policy of uid {uid} was not published, MediaProvider stays unguarded");
            return None;
        }
        let Some(dex) = dex else {
            cleaner_core::log_e!("media guard: companion sent no {}", DEX_NAME.to_string_lossy());
            return None;
        };
        Some(PendingGuard { dex, policy_file })
    }

    pub fn arm(self, env: *mut JNIEnv) {
        if env.is_null() {
            cleaner_core::log_e!("media guard: no JNIEnv in postAppSpecialize");
            return;
        }
        unsafe {
            if load_and_arm(env, &self.dex, &self.policy_file) {
                cleaner_core::log_i!("media guard: dex loaded into MediaProvider");
            } else {
                cleaner_core::log_e!("media guard: loading the dex failed, MediaProvider stays unguarded");
            }
            clear_exception(env);
        }
    }
}

fn receive_dex(fd: c_int) -> Option<Vec<u8>> {
    let length = proto::read_u32(fd)?;
    if length == 0 || u64::from(length) > MAX_DEX_LEN {
        return None;
    }
    let mut dex = vec![0u8; length as usize];
    proto::read_exact(fd, &mut dex).then_some(dex)
}

fn read_dex() -> Vec<u8> {
    let path = format!("{}/{}", consts::MODULE_DIR, DEX_NAME.to_string_lossy());
    let read = File::open(&path).and_then(|file| {
        let mut dex = Vec::new();
        file.take(MAX_DEX_LEN).read_to_end(&mut dex)?;
        Ok(dex)
    });
    match read {
        Ok(dex) => dex,
        Err(error) => {
            cleaner_core::log_e!("media guard: reading {path} failed: {error}");
            Vec::new()
        }
    }
}

macro_rules! jni {
    ($env:expr, $name:ident $(, $arg:expr)* $(,)?) => {
        match (**$env).$name {
            Some(function) => function($env $(, $arg)*),
            None => return false,
        }
    };
}

unsafe fn load_and_arm(env: *mut JNIEnv, dex: &[u8], policy_file: &CStr) -> bool {
    if !exempt_hidden_apis(env) {
        return false;
    }

    let Ok(length) = jsize::try_from(dex.len()) else {
        return false;
    };
    let bytes = jni!(env, NewByteArray, length);
    if failed(env, bytes) {
        return false;
    }
    jni!(env, SetByteArrayRegion, bytes, 0, length, dex.as_ptr().cast());

    let Some(buffer_class) = find_class(env, c"java/nio/ByteBuffer") else {
        return false;
    };
    let wrap = jni!(env, GetStaticMethodID, buffer_class, c"wrap".as_ptr(), c"([B)Ljava/nio/ByteBuffer;".as_ptr());
    if failed(env, wrap) {
        return false;
    }
    let buffer = jni!(env, CallStaticObjectMethodA, buffer_class, wrap, [jvalue { l: bytes }].as_ptr());
    if failed(env, buffer) {
        return false;
    }

    let Some(loader_class) = find_class(env, c"java/lang/ClassLoader") else {
        return false;
    };
    let system_loader = jni!(
        env,
        GetStaticMethodID,
        loader_class,
        c"getSystemClassLoader".as_ptr(),
        c"()Ljava/lang/ClassLoader;".as_ptr(),
    );
    if failed(env, system_loader) {
        return false;
    }
    let parent = jni!(env, CallStaticObjectMethodA, loader_class, system_loader, ptr::null());
    if failed(env, parent) {
        return false;
    }

    let Some(dex_loader_class) = find_class(env, c"dalvik/system/InMemoryDexClassLoader") else {
        return false;
    };
    let constructor = jni!(
        env,
        GetMethodID,
        dex_loader_class,
        c"<init>".as_ptr(),
        c"(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V".as_ptr(),
    );
    if failed(env, constructor) {
        return false;
    }
    let loader = jni!(env, NewObjectA, dex_loader_class, constructor, [jvalue { l: buffer }, jvalue { l: parent }].as_ptr());
    if failed(env, loader) {
        return false;
    }

    let load_class = jni!(
        env,
        GetMethodID,
        loader_class,
        c"loadClass".as_ptr(),
        c"(Ljava/lang/String;)Ljava/lang/Class;".as_ptr(),
    );
    if failed(env, load_class) {
        return false;
    }
    let entry_name = jni!(env, NewStringUTF, c"killua.dev.cleaner.guard.MediaGuard".as_ptr());
    if failed(env, entry_name) {
        return false;
    }
    let entry: jclass = jni!(env, CallObjectMethodA, loader, load_class, [jvalue { l: entry_name }].as_ptr());
    if failed(env, entry) {
        return false;
    }
    let arm = jni!(env, GetStaticMethodID, entry, c"arm".as_ptr(), c"(Ljava/lang/String;)V".as_ptr());
    if failed(env, arm) {
        return false;
    }
    let path = jni!(env, NewStringUTF, policy_file.as_ptr());
    if failed(env, path) {
        return false;
    }
    jni!(env, CallStaticVoidMethodA, entry, arm, [jvalue { l: path }].as_ptr());
    true
}

unsafe fn exempt_hidden_apis(env: *mut JNIEnv) -> bool {
    let Some(runtime_class) = find_class(env, c"dalvik/system/VMRuntime") else {
        return false;
    };
    let get_runtime = jni!(
        env,
        GetStaticMethodID,
        runtime_class,
        c"getRuntime".as_ptr(),
        c"()Ldalvik/system/VMRuntime;".as_ptr(),
    );
    if failed(env, get_runtime) {
        return false;
    }
    let runtime = jni!(env, CallStaticObjectMethodA, runtime_class, get_runtime, ptr::null());
    if failed(env, runtime) {
        return false;
    }
    let set_exemptions = jni!(
        env,
        GetMethodID,
        runtime_class,
        c"setHiddenApiExemptions".as_ptr(),
        c"([Ljava/lang/String;)V".as_ptr(),
    );
    if failed(env, set_exemptions) {
        return false;
    }
    let Some(string_class) = find_class(env, c"java/lang/String") else {
        return false;
    };
    let everything = jni!(env, NewStringUTF, c"L".as_ptr());
    if failed(env, everything) {
        return false;
    }
    let prefixes = jni!(env, NewObjectArray, 1, string_class, everything);
    if failed(env, prefixes) {
        return false;
    }
    jni!(env, CallVoidMethodA, runtime, set_exemptions, [jvalue { l: prefixes }].as_ptr());
    !pending_exception(env)
}

unsafe fn find_class(env: *mut JNIEnv, name: &CStr) -> Option<jclass> {
    let class = (**env).FindClass?(env, name.as_ptr());
    (!failed(env, class)).then_some(class)
}

unsafe fn failed<T>(env: *mut JNIEnv, value: *mut T) -> bool {
    value.is_null() || pending_exception(env)
}

unsafe fn pending_exception(env: *mut JNIEnv) -> bool {
    (**env).ExceptionCheck.is_some_and(|check| check(env) != 0)
}

unsafe fn clear_exception(env: *mut JNIEnv) {
    if pending_exception(env) {
        if let Some(clear) = (**env).ExceptionClear {
            clear(env);
        }
    }
}

static PUBLISHERS: Mutex<Option<HashSet<u32>>> = Mutex::new(None);

pub fn serve_media_guard(client: c_int) {
    let Some(uid) = proto::read_u32(client) else {
        return;
    };
    let user_id = uid / consts::PER_USER_RANGE;
    let published = policy::publish(user_id);
    if !published {
        cleaner_core::log_w!("publishing the provider policy of user {user_id} failed");
    }
    keep_published(user_id);
    if !proto::write_all(client, &[u8::from(published)]) || !published {
        return;
    }
    let dex = read_dex();
    let _ = proto::write_all(client, &(dex.len() as u32).to_le_bytes()) && proto::write_all(client, &dex);
}

fn keep_published(user_id: u32) {
    let Ok(mut guard) = PUBLISHERS.lock() else {
        return;
    };
    if !guard.get_or_insert_with(HashSet::new).insert(user_id) {
        return;
    }
    let spawned = std::thread::Builder::new()
        .name(format!("cleaner-policy-{user_id}"))
        .spawn(move || publish_on_change(user_id));
    if spawned.is_err() {
        guard.get_or_insert_with(HashSet::new).remove(&user_id);
    }
}

fn publish_on_change(user_id: u32) {
    let rules_dir = format!("{}/{}", consts::RULES_DIR, user_id);
    fsmeta::make_dir_tree(&rules_dir, None);
    let watch = RulesWatch::open(&rules_dir);
    loop {
        watch.wait();
        policy::publish(user_id);
    }
}

struct RulesWatch {
    fd: c_int,
}

impl RulesWatch {
    fn open(dir: &str) -> RulesWatch {
        let fd = unsafe { libc::inotify_init1(libc::IN_CLOEXEC | libc::IN_NONBLOCK) };
        let watched = fd >= 0
            && CString::new(dir).is_ok_and(|path| {
                let mask = libc::IN_CLOSE_WRITE | libc::IN_MOVED_TO | libc::IN_MOVED_FROM | libc::IN_DELETE;
                unsafe { libc::inotify_add_watch(fd, path.as_ptr(), mask) >= 0 }
            });
        if fd >= 0 && !watched {
            unsafe { libc::close(fd) };
            return RulesWatch { fd: -1 };
        }
        RulesWatch { fd }
    }

    fn wait(&self) {
        let mut poll = libc::pollfd { fd: self.fd, events: libc::POLLIN, revents: 0 };
        let timeout = if self.fd >= 0 { -1 } else { RESCAN_MS };
        let ready = unsafe { libc::poll(&mut poll, 1, timeout) };
        if ready > 0 && poll.revents & libc::POLLIN != 0 {
            std::thread::sleep(std::time::Duration::from_millis(SETTLE_MS));
            self.drain();
        }
    }

    fn drain(&self) {
        let mut scratch = [0u8; 4096];
        while unsafe { libc::read(self.fd, scratch.as_mut_ptr().cast(), scratch.len()) } > 0 {}
    }
}
