use std::os::raw::c_int;
use std::os::unix::fs::MetadataExt;
use std::sync::Once;

use cleaner_core::consts;
use cleaner_core::rule::{self, Rule};

use crate::{proto, watch};

const SOCKET_NAME: &[u8] = b"killua.dev.cleaner.isolation";

pub fn request(package: &str) -> bool {
    let fd = unsafe { libc::socket(libc::AF_UNIX, libc::SOCK_STREAM | libc::SOCK_CLOEXEC, 0) };
    if fd < 0 {
        return false;
    }
    let (address, length) = address();
    let connected =
        unsafe { libc::connect(fd, (&address as *const libc::sockaddr_un).cast(), length) } == 0;
    let bytes = package.as_bytes();
    proto::set_receive_timeout(fd, 5000);
    let mounted = connected
        && proto::write_all(fd, &(bytes.len() as u32).to_le_bytes())
        && proto::write_all(fd, bytes)
        && proto::read_u8(fd) == Some(proto::RESULT_MOUNTED);
    unsafe { libc::close(fd) };
    mounted
}

pub fn ensure_listening() {
    static LISTENER: Once = Once::new();
    LISTENER.call_once(|| {
        let _ = std::thread::Builder::new().name(String::from("cleaner-handoff")).spawn(listen);
    });
}

fn listen() {
    let fd = unsafe { libc::socket(libc::AF_UNIX, libc::SOCK_STREAM | libc::SOCK_CLOEXEC, 0) };
    if fd < 0 {
        return;
    }
    let (address, length) = address();
    let bound = unsafe { libc::bind(fd, (&address as *const libc::sockaddr_un).cast(), length) } == 0
        && unsafe { libc::listen(fd, 16) } == 0;
    if !bound {
        unsafe { libc::close(fd) };
        return;
    }
    loop {
        let client = unsafe { libc::accept4(fd, std::ptr::null_mut(), std::ptr::null_mut(), libc::SOCK_CLOEXEC) };
        if client < 0 {
            continue;
        }
        let spawned = std::thread::Builder::new()
            .name(String::from("cleaner-mount"))
            .spawn(move || {
                serve(client);
                unsafe { libc::close(client) };
            });
        if spawned.is_err() {
            unsafe { libc::close(client) };
        }
    }
}

fn serve(client: c_int) {
    proto::set_receive_timeout(client, 3000);
    let Some((pid, uid)) = peer(client) else {
        return;
    };
    let Some(length) = proto::read_u32(client) else {
        return;
    };
    if length == 0 || length > proto::MAX_PACKAGE_LEN {
        return;
    }
    let mut buffer = vec![0u8; length as usize];
    if !proto::read_exact(client, &mut buffer) {
        return;
    }
    let Ok(package) = String::from_utf8(buffer) else {
        return;
    };

    let user_id = uid / consts::PER_USER_RANGE;
    let rule = owns_package(uid, &package)
        .then(|| Rule::load_enabled(user_id, &package))
        .flatten();
    let Some(rule) = rule else {
        cleaner_core::log_w!("refusing mount request of uid {uid} for {package}");
        let _ = proto::write_all(client, &[proto::RESULT_FAILED]);
        return;
    };
    let mounted = cleaner_core::isolate(pid, uid, &package, &rule);
    let _ = proto::write_all(
        client,
        &[if mounted { proto::RESULT_MOUNTED } else { proto::RESULT_FAILED }],
    );
    if mounted {
        watch::spawn_if_needed(pid, uid, package, rule);
    }
}

fn owns_package(uid: u32, package: &str) -> bool {
    rule::is_valid_package(package)
        && std::fs::metadata(format!("/data/user_de/{}/{package}", uid / consts::PER_USER_RANGE))
            .is_ok_and(|meta| meta.uid() == uid)
}

fn peer(fd: c_int) -> Option<(i32, u32)> {
    let mut cred: libc::ucred = unsafe { std::mem::zeroed() };
    let mut len = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
    let rc = unsafe {
        libc::getsockopt(
            fd,
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            (&mut cred as *mut libc::ucred).cast(),
            &mut len,
        )
    };
    (rc == 0 && cred.pid > 0).then_some((cred.pid, cred.uid))
}

fn address() -> (libc::sockaddr_un, libc::socklen_t) {
    let mut address: libc::sockaddr_un = unsafe { std::mem::zeroed() };
    address.sun_family = libc::AF_UNIX as libc::sa_family_t;
    for (slot, byte) in address.sun_path[1..].iter_mut().zip(SOCKET_NAME) {
        *slot = *byte as libc::c_char;
    }
    let length = std::mem::size_of::<libc::sa_family_t>() + 1 + SOCKET_NAME.len();
    (address, length as libc::socklen_t)
}
