use std::os::raw::{c_char, c_int, c_long, c_void};

use jni_sys::{jboolean, jint, jintArray, jobjectArray, jstring, JNIEnv, JNINativeMethod};

pub const ZYGISK_API_VERSION: c_long = 4;

pub const OPTION_DLCLOSE_MODULE_LIBRARY: c_int = 1;

/// `zygisk::AppSpecializeArgs`
#[repr(C)]
pub struct AppSpecializeArgs {
    pub uid: *mut jint,
    pub gid: *mut jint,
    pub gids: *mut jintArray,
    pub runtime_flags: *mut jint,
    pub rlimits: *mut jobjectArray,
    pub mount_external: *mut jint,
    pub se_info: *mut jstring,
    pub nice_name: *mut jstring,
    pub instruction_set: *mut jstring,
    pub app_data_dir: *mut jstring,
    pub fds_to_ignore: *mut jintArray,
    pub is_child_zygote: *mut jboolean,
    pub is_top_app: *mut jboolean,
    pub pkg_data_info_list: *mut jobjectArray,
    pub whitelisted_data_info_list: *mut jobjectArray,
    pub mount_data_dirs: *mut jboolean,
    pub mount_storage_dirs: *mut jboolean,
}

#[repr(C)]
pub struct ModuleAbi {
    pub api_version: c_long,
    pub impl_: *mut c_void,
    pub pre_app_specialize: unsafe extern "C" fn(*mut c_void, *mut AppSpecializeArgs),
    pub post_app_specialize: unsafe extern "C" fn(*mut c_void, *const AppSpecializeArgs),
    pub pre_server_specialize: unsafe extern "C" fn(*mut c_void, *mut c_void),
    pub post_server_specialize: unsafe extern "C" fn(*mut c_void, *const c_void),
}

#[repr(C)]
pub struct ApiTable {
    pub impl_: *mut c_void,
    pub register_module: Option<unsafe extern "C" fn(*mut ApiTable, *mut ModuleAbi) -> bool>,
    pub hook_jni_native_methods:
        Option<unsafe extern "C" fn(*mut JNIEnv, *const c_char, *mut JNINativeMethod, c_int)>,
    pub plt_hook_register: Option<
        unsafe extern "C" fn(libc::dev_t, libc::ino_t, *const c_char, *mut c_void, *mut *mut c_void),
    >,
    pub exempt_fd: Option<unsafe extern "C" fn(c_int) -> bool>,
    pub plt_hook_commit: Option<unsafe extern "C" fn() -> bool>,
    pub connect_companion: Option<unsafe extern "C" fn(*mut c_void) -> c_int>,
    pub set_option: Option<unsafe extern "C" fn(*mut c_void, c_int)>,
    pub get_module_dir: Option<unsafe extern "C" fn(*mut c_void) -> c_int>,
    pub get_flags: Option<unsafe extern "C" fn(*mut c_void) -> u32>,
}

pub struct Api {
    table: *mut ApiTable,
}

impl Api {
    pub fn new(table: *mut ApiTable) -> Api {
        Api { table }
    }

    pub fn connect_companion(&self) -> c_int {
        unsafe {
            match (*self.table).connect_companion {
                Some(connect) => connect((*self.table).impl_),
                None => -1,
            }
        }
    }

    pub fn set_option(&self, option: c_int) {
        unsafe {
            if let Some(set) = (*self.table).set_option {
                set((*self.table).impl_, option);
            }
        }
    }
}
