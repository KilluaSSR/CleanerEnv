pub struct MountEntry {
    pub mount_point: String,
    pub fs_type: String,
}

pub fn read(pid: i32) -> Vec<MountEntry> {
    let path = if pid < 0 {
        String::from("/proc/self/mountinfo")
    } else {
        format!("/proc/{pid}/mountinfo")
    };
    let Ok(text) = std::fs::read_to_string(path) else {
        return Vec::new();
    };
    let mut entries = Vec::new();
    for line in text.lines() {
        if let Some(entry) = parse_line(line) {
            entries.push(entry);
        }
    }
    entries
}

fn parse_line(line: &str) -> Option<MountEntry> {
    let separator = line.find(" - ")?;
    let (head, tail) = line.split_at(separator);
    let mount_point = head.split_whitespace().nth(4)?;
    let fs_type = tail[3..].split_whitespace().next()?;
    Some(MountEntry {
        mount_point: unescape(mount_point),
        fs_type: String::from(fs_type),
    })
}

fn unescape(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = String::with_capacity(value.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'\\' && i + 3 < bytes.len() {
            let digits = &value[i + 1..i + 4];
            if let Ok(code) = u8::from_str_radix(digits, 8) {
                out.push(code as char);
                i += 4;
                continue;
            }
        }
        out.push(bytes[i] as char);
        i += 1;
    }
    out
}

pub fn is_mounted(entries: &[MountEntry], mount_point: &str) -> bool {
    entries.iter().any(|entry| entry.mount_point == mount_point)
}
