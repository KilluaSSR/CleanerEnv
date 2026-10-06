use std::fs::{self, OpenOptions};
use std::io::Write;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::consts;
use crate::env;

fn now_millis() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0)
}

fn ensure_run_dir() {
    if fs::create_dir_all(consts::RUN_DIR).is_ok() {
        let _ = fs::set_permissions(consts::DATA_DIR, fs::Permissions::from_mode(0o700));
    }
}

pub fn write_heartbeat(abi: &str) {
    ensure_run_dir();
    let content = format!(
        "version={}\nboot_id={}\npid={}\nabi={}\ntime={}\n",
        consts::MODULE_VERSION_CODE,
        env::boot_id(),
        std::process::id(),
        abi,
        now_millis(),
    );
    let temp = format!("{}.tmp", consts::ZYGISK_STATE_FILE);
    if fs::write(&temp, content).is_ok() {
        let _ = fs::rename(&temp, consts::ZYGISK_STATE_FILE);
    }
}

pub fn append_mount_log(pid: i32, uid: u32, package: &str, ok: bool, message: &str) {
    ensure_run_dir();
    let too_big = fs::metadata(consts::MOUNT_LOG_FILE)
        .map(|m| m.len() > consts::MOUNT_LOG_LIMIT)
        .unwrap_or(false);
    let mut options = OpenOptions::new();
    options.create(true).mode(0o600);
    if too_big {
        options.write(true).truncate(true);
    } else {
        options.append(true);
    }
    if let Ok(mut file) = options.open(consts::MOUNT_LOG_FILE) {
        let message: String = message.chars().filter(|c| *c != '\t' && *c != '\n').collect();
        let line = format!(
            "{}\t{pid}\t{uid}\t{package}\t{}\t{message}\n",
            now_millis(),
            if ok { 1 } else { 0 },
        );
        let _ = file.write_all(line.as_bytes());
    }
}
