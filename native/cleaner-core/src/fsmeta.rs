use std::ffi::{CStr, CString};

const SELINUX_XATTR: &[u8] = b"security.selinux\0";

pub struct DirMeta {
    pub uid: u32,
    pub gid: u32,
    pub mode: u32,
    label: Option<Vec<u8>>,
}

pub fn exists(path: &CStr) -> bool {
    unsafe { libc::access(path.as_ptr(), libc::F_OK) == 0 }
}

pub fn read_meta(path: &CStr) -> Option<DirMeta> {
    let mut st: libc::stat = unsafe { std::mem::zeroed() };
    if unsafe { libc::stat(path.as_ptr(), &mut st) } != 0 {
        return None;
    }
    Some(DirMeta {
        uid: st.st_uid,
        gid: st.st_gid,
        mode: st.st_mode & 0o7777,
        label: read_label(path),
    })
}

fn read_label(path: &CStr) -> Option<Vec<u8>> {
    let mut buf = [0u8; 256];
    let len = unsafe {
        libc::getxattr(
            path.as_ptr(),
            SELINUX_XATTR.as_ptr().cast(),
            buf.as_mut_ptr().cast(),
            buf.len(),
        )
    };
    (len > 0).then(|| buf[..len as usize].iter().copied().collect())
}

fn apply_meta(path: &CStr, meta: &DirMeta, owner: Option<(u32, u32)>) {
    let (uid, gid) = owner.unwrap_or((meta.uid, meta.gid));
    unsafe {
        libc::chown(path.as_ptr(), uid, gid);
        libc::chmod(path.as_ptr(), meta.mode as libc::mode_t);
    }
    if let Some(label) = &meta.label {
        unsafe {
            libc::setxattr(
                path.as_ptr(),
                SELINUX_XATTR.as_ptr().cast(),
                label.as_ptr().cast(),
                label.len(),
                0,
            );
        }
    }
}

pub fn make_dir_tree(path: &str, owner: Option<(u32, u32)>) -> bool {
    let mut missing: Vec<&str> = Vec::new();
    let mut current = path.trim_end_matches('/');
    loop {
        let Ok(probe) = CString::new(current) else {
            return false;
        };
        if exists(&probe) {
            break;
        }
        missing.push(current);
        match current.rfind('/') {
            Some(index) if index > 0 => current = &current[..index],
            _ => return false,
        }
    }
    let Ok(anchor) = CString::new(current) else {
        return false;
    };
    let Some(meta) = read_meta(&anchor) else {
        return false;
    };
    for dir in missing.iter().rev() {
        let Ok(dir) = CString::new(*dir) else {
            return false;
        };
        let created = unsafe { libc::mkdir(dir.as_ptr(), meta.mode as libc::mode_t) } == 0;
        if !created && std::io::Error::last_os_error().raw_os_error() != Some(libc::EEXIST) {
            return false;
        }
        apply_meta(&dir, &meta, owner);
    }
    true
}

pub fn open_up_tree(path: &str, budget: &mut usize) {
    if *budget == 0 {
        return;
    }
    *budget -= 1;
    let Ok(target) = CString::new(path) else {
        return;
    };
    let Some(meta) = read_meta(&target) else {
        return;
    };
    let is_dir = std::fs::metadata(path).map(|m| m.is_dir()).unwrap_or(false);
    let extra = if is_dir { 0o777 } else { 0o666 };
    unsafe {
        libc::chmod(target.as_ptr(), ((meta.mode & 0o7000) | extra) as libc::mode_t);
    }
    if !is_dir {
        return;
    }
    if let Ok(entries) = std::fs::read_dir(path) {
        for entry in entries.flatten() {
            if let Some(child) = entry.path().to_str() {
                open_up_tree(child, budget);
            }
        }
    }
}
