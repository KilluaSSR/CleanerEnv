use std::ffi::CString;
use std::os::raw::c_int;

use crate::plan::MountPlan;

const MNT_DETACH: c_int = 2;
const UMOUNT_NOFOLLOW: c_int = 8;
const FD_PATH_PREFIX: &[u8] = b"/proc/self/fd/";
const FD_PATH_LEN: usize = 32;

pub enum MountOutcome {
    Mounted(usize),
    Failed(String),
}

pub fn apply(pid: i32, plan: &MountPlan) -> MountOutcome {
    let ns_path = match CString::new(format!("/proc/{pid}/ns/mnt")) {
        Ok(path) => path,
        Err(_) => return MountOutcome::Failed(String::from("bad pid")),
    };
    if plan.sources.is_empty() || plan.sources.len() != plan.targets.len() {
        return MountOutcome::Failed(String::from("empty plan"));
    }
    let mut fd_paths = vec![[0u8; FD_PATH_LEN]; plan.sources.len()];

    let mut fds = [0 as c_int; 2];
    if unsafe { libc::socketpair(libc::AF_UNIX, libc::SOCK_STREAM, 0, fds.as_mut_ptr()) } != 0 {
        return MountOutcome::Failed(String::from("socketpair"));
    }
    let (parent_fd, child_fd) = (fds[0], fds[1]);

    let child = unsafe { libc::fork() };
    if child < 0 {
        unsafe {
            libc::close(parent_fd);
            libc::close(child_fd);
        }
        return MountOutcome::Failed(String::from("fork"));
    }

    if child == 0 {
        unsafe { libc::close(parent_fd) };
        let code = child_body(&ns_path, plan, &mut fd_paths);
        unsafe {
            let byte = [code as u8];
            libc::write(child_fd, byte.as_ptr().cast(), 1);
            libc::_exit(0);
        }
    }

    unsafe { libc::close(child_fd) };
    let outcome = read_child(parent_fd, child);
    unsafe { libc::close(parent_fd) };
    reap(child);
    outcome
}

fn child_body(ns_path: &CString, plan: &MountPlan, fd_paths: &mut [[u8; FD_PATH_LEN]]) -> i32 {
    let ns_fd = unsafe { libc::open(ns_path.as_ptr(), libc::O_RDONLY | libc::O_CLOEXEC) };
    if ns_fd < 0 {
        return -1;
    }
    if unsafe { libc::setns(ns_fd, 0) } != 0 {
        return -2;
    }
    unsafe { libc::close(ns_fd) };

    for (source, path) in plan.sources.iter().zip(fd_paths.iter_mut()) {
        let fd = unsafe { libc::open(source.as_ptr(), libc::O_PATH | libc::O_DIRECTORY | libc::O_CLOEXEC) };
        if fd >= 0 {
            write_fd_path(fd, path);
        }
    }
    if fd_paths[0][0] == 0 {
        return -3;
    }

    unsafe { libc::umount2(plan.view_root.as_ptr(), MNT_DETACH | UMOUNT_NOFOLLOW) };
    if !bind(&fd_paths[0], &plan.targets[0]) {
        return 0;
    }
    unsafe {
        libc::mount(
            std::ptr::null(),
            plan.view_root.as_ptr(),
            std::ptr::null(),
            (libc::MS_REC | libc::MS_SLAVE) as libc::c_ulong,
            std::ptr::null(),
        );
    }
    let mut mounted = 1;
    for (path, target) in fd_paths.iter().zip(plan.targets.iter()).skip(1) {
        if path[0] != 0 && bind(path, target) {
            mounted += 1;
        }
    }
    mounted.min(i8::MAX as i32)
}

fn bind(source: &[u8; FD_PATH_LEN], target: &CString) -> bool {
    unsafe {
        libc::mount(
            source.as_ptr().cast(),
            target.as_ptr(),
            std::ptr::null(),
            (libc::MS_BIND | libc::MS_REC) as libc::c_ulong,
            std::ptr::null(),
        ) == 0
    }
}

/// `/proc/self/fd/<fd>` as a NUL terminated string, without allocating.
fn write_fd_path(fd: c_int, out: &mut [u8; FD_PATH_LEN]) {
    let mut digits = [0u8; 12];
    let mut count = 0;
    let mut value = fd as u32;
    loop {
        digits[count] = b'0' + (value % 10) as u8;
        count += 1;
        value /= 10;
        if value == 0 {
            break;
        }
    }
    out[..FD_PATH_PREFIX.len()].copy_from_slice(FD_PATH_PREFIX);
    for index in 0..count {
        out[FD_PATH_PREFIX.len() + index] = digits[count - 1 - index];
    }
    out[FD_PATH_PREFIX.len() + count] = 0;
}

fn read_child(fd: c_int, child: i32) -> MountOutcome {
    set_timeout(fd, 2);
    let mut byte = [0u8; 1];
    let read = unsafe { libc::read(fd, byte.as_mut_ptr().cast(), 1) };
    if read != 1 {
        unsafe { libc::kill(child, libc::SIGKILL) };
        return MountOutcome::Failed(String::from("child timed out in setns"));
    }
    match byte[0] as i8 {
        mounted if mounted > 0 => MountOutcome::Mounted(mounted as usize),
        0 => MountOutcome::Failed(String::from("bind isolated root")),
        -1 => MountOutcome::Failed(String::from("open ns")),
        -2 => MountOutcome::Failed(String::from("setns")),
        -3 => MountOutcome::Failed(String::from("open isolated root")),
        other => MountOutcome::Failed(format!("mount op {other}")),
    }
}

fn set_timeout(fd: c_int, seconds: i64) {
    let tv = libc::timeval { tv_sec: seconds as libc::time_t, tv_usec: 0 };
    unsafe {
        libc::setsockopt(
            fd,
            libc::SOL_SOCKET,
            libc::SO_RCVTIMEO,
            (&tv as *const libc::timeval).cast(),
            std::mem::size_of::<libc::timeval>() as libc::socklen_t,
        );
    }
}

fn reap(child: i32) {
    let mut status = 0;
    for _ in 0..100 {
        let rc = unsafe { libc::waitpid(child, &mut status, libc::WNOHANG) };
        if rc == child {
            return;
        }
        unsafe { libc::usleep(10_000) };
    }
    unsafe {
        libc::kill(child, libc::SIGKILL);
        libc::waitpid(child, &mut status, 0);
    }
}
