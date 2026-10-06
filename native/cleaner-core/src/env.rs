use std::ffi::CString;
use std::os::raw::c_char;

pub enum Backend {
    /// Android 11+: `/storage` is a FUSE view served by MediaProvider.
    Fuse,
    /// Android 10: `/storage` is an sdcardfs view of `/mnt/runtime/<mode>`.
    Sdcardfs,
}

pub fn property(name: &str) -> String {
    let Ok(key) = CString::new(name) else {
        return String::new();
    };
    let mut value = [0 as c_char; libc::PROP_VALUE_MAX as usize];
    let len = unsafe { libc::__system_property_get(key.as_ptr(), value.as_mut_ptr()) };
    if len <= 0 {
        return String::new();
    }
    let bytes: Vec<u8> = value[..len as usize].iter().map(|c| *c as u8).collect();
    String::from_utf8_lossy(&bytes).into_owned()
}

pub fn sdk_int() -> i32 {
    property("ro.build.version.sdk").trim().parse().unwrap_or(0)
}

pub fn backend() -> Backend {
    match property("persist.sys.fuse").as_str() {
        "true" => Backend::Fuse,
        "false" => Backend::Sdcardfs,
        _ if sdk_int() >= 30 => Backend::Fuse,
        _ => Backend::Sdcardfs,
    }
}

pub fn boot_id() -> String {
    std::fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .map(|id| String::from(id.trim()))
        .unwrap_or_default()
}
