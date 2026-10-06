use std::ffi::CString;

use cleaner_core::env::{self, Backend};
use cleaner_core::mountinfo;
use cleaner_core::rule::Rule;

const POLL_INTERVAL_MS: i32 = 60_000;

pub fn spawn_if_needed(pid: i32, uid: u32, package: String, rule: Rule) {
    if !matches!(env::backend(), Backend::Sdcardfs) {
        return;
    }
    let _ = std::thread::Builder::new()
        .name(String::from("cleaner-watch"))
        .spawn(move || watch(pid, uid, &package, &rule));
}

fn watch(pid: i32, uid: u32, package: &str, rule: &Rule) {
    let Ok(path) = CString::new(format!("/proc/{pid}/mountinfo")) else {
        return;
    };
    let fd = unsafe { libc::open(path.as_ptr(), libc::O_RDONLY | libc::O_CLOEXEC) };
    if fd < 0 {
        return;
    }
    let view_root = format!("/storage/emulated/{}", uid / cleaner_core::consts::PER_USER_RANGE);
    let mut scratch = [0u8; 4096];
    loop {
        let mut poll = libc::pollfd { fd, events: libc::POLLPRI | libc::POLLERR, revents: 0 };
        let ready = unsafe { libc::poll(&mut poll, 1, POLL_INTERVAL_MS) };
        if unsafe { libc::kill(pid, 0) } != 0 {
            break;
        }
        if ready <= 0 {
            continue;
        }
        unsafe {
            libc::lseek(fd, 0, libc::SEEK_SET);
            while libc::read(fd, scratch.as_mut_ptr().cast(), scratch.len()) > 0 {}
        }
        if !mountinfo::is_mounted(&mountinfo::read(pid), &view_root) {
            cleaner_core::isolate(pid, uid, package, rule);
        }
    }
    unsafe { libc::close(fd) };
}
