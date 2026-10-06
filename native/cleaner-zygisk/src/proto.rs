use std::os::raw::c_int;

pub const OP_ISOLATE: u8 = 1;
pub const OP_MEDIA_GUARD: u8 = 2;

pub const VERDICT_SKIP: u8 = 0;
pub const VERDICT_ISOLATE: u8 = 1;
pub const RESULT_FAILED: u8 = 0;
pub const RESULT_MOUNTED: u8 = 1;

pub const MAX_PACKAGE_LEN: u32 = 255;

pub fn write_all(fd: c_int, mut bytes: &[u8]) -> bool {
    while !bytes.is_empty() {
        let written = unsafe { libc::write(fd, bytes.as_ptr().cast(), bytes.len()) };
        if written <= 0 {
            if written < 0 && errno() == libc::EINTR {
                continue;
            }
            return false;
        }
        bytes = &bytes[written as usize..];
    }
    true
}

pub fn read_exact(fd: c_int, buffer: &mut [u8]) -> bool {
    let mut filled = 0;
    while filled < buffer.len() {
        let read = unsafe {
            libc::read(fd, buffer[filled..].as_mut_ptr().cast(), buffer.len() - filled)
        };
        if read <= 0 {
            if read < 0 && errno() == libc::EINTR {
                continue;
            }
            return false;
        }
        filled += read as usize;
    }
    true
}

pub fn read_u8(fd: c_int) -> Option<u8> {
    let mut byte = [0u8; 1];
    read_exact(fd, &mut byte).then_some(byte[0])
}

pub fn read_u32(fd: c_int) -> Option<u32> {
    let mut raw = [0u8; 4];
    read_exact(fd, &mut raw).then(|| u32::from_le_bytes(raw))
}

pub fn set_receive_timeout(fd: c_int, millis: i64) {
    let tv = libc::timeval {
        tv_sec: (millis / 1000) as libc::time_t,
        tv_usec: ((millis % 1000) * 1000) as libc::suseconds_t,
    };
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

fn errno() -> c_int {
    std::io::Error::last_os_error().raw_os_error().unwrap_or(0)
}
