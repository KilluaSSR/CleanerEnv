pub const APPLICATION_ID: &str = "killua.dev.cleaner";
pub const DATA_DIR: &str = "/data/adb/cleaner";
pub const RULES_DIR: &str = "/data/adb/cleaner/rules";
pub const RUN_DIR: &str = "/data/adb/cleaner/run";
pub const ZYGISK_STATE_FILE: &str = "/data/adb/cleaner/run/zygisk.state";
pub const MOUNT_LOG_FILE: &str = "/data/adb/cleaner/run/mounts.log";
pub const RULE_FILE_SUFFIX: &str = ".rule";

pub const LOCATION_CACHE: &str = "2";

pub const PER_USER_RANGE: u32 = 100_000;
pub const FIRST_APPLICATION_UID: u32 = 10_000;
pub const LAST_APPLICATION_UID: u32 = 19_999;

pub const AID_EXT_DATA_RW: u32 = 1078;
pub const AID_MEDIA_RW: u32 = 1023;

pub const MOUNT_LOG_LIMIT: u64 = 512 * 1024;

pub const GALLERY_FOLDERS: [&str; 3] = ["DCIM", "Pictures", "Movies"];

pub const MODULE_VERSION_CODE: u32 = 1;

pub const MODULE_DIR: &str = "/data/adb/modules/cleaner_isolation";
