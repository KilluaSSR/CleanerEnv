use std::os::raw::c_int;

pub const INFO: c_int = 4;
pub const WARN: c_int = 5;
pub const ERROR: c_int = 6;

#[cfg(target_os = "android")]
mod sink {
    use std::ffi::CString;
    use std::os::raw::{c_char, c_int};

    const TAG: &[u8] = b"CleanerIsolation\0";

    #[link(name = "log")]
    extern "C" {
        fn __android_log_write(priority: c_int, tag: *const c_char, text: *const c_char) -> c_int;
    }

    pub fn write(priority: c_int, message: &str) {
        let sanitized: String = message.chars().filter(|c| *c != '\0').collect();
        if let Ok(text) = CString::new(sanitized) {
            unsafe {
                __android_log_write(priority, TAG.as_ptr().cast(), text.as_ptr());
            }
        }
    }
}

#[cfg(not(target_os = "android"))]
mod sink {
    pub fn write(priority: std::os::raw::c_int, message: &str) {
        eprintln!("[{priority}] {message}");
    }
}

pub fn write(priority: c_int, message: &str) {
    sink::write(priority, message);
}

#[macro_export]
macro_rules! log_i {
    ($($arg:tt)*) => { $crate::log::write($crate::log::INFO, &format!($($arg)*)) };
}

#[macro_export]
macro_rules! log_w {
    ($($arg:tt)*) => { $crate::log::write($crate::log::WARN, &format!($($arg)*)) };
}

#[macro_export]
macro_rules! log_e {
    ($($arg:tt)*) => { $crate::log::write($crate::log::ERROR, &format!($($arg)*)) };
}
