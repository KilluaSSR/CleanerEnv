package killua.dev.cleaner.ipc

object IpcConstants {
    const val SERVER_VERSION = 1

    const val APPLICATION_ID = "killua.dev.cleaner"

    const val SERVER_NICE_NAME = "cleaner_server"
    const val SERVER_MAIN_CLASS = "killua.dev.cleaner.server.ServerMain"
    const val STARTER_LIBRARY = "libcleaner_starter.so"

    const val BINDER_PROVIDER_SUFFIX = ".binder"
    const val METHOD_SEND_BINDER = "sendBinder"
    const val EXTRA_BINDER = "killua.dev.cleaner.extra.BINDER"

    const val SERVER_SOCKET = "killua_cleaner_server"
    const val SOCKET_REQUEST_BINDER: Byte = 1

    const val MODULE_ID = "cleaner_isolation"
    const val MODULES_DIR = "/data/adb/modules"

    const val DATA_DIR = "/data/adb/cleaner"
    const val RULES_DIR = "$DATA_DIR/rules"
    const val RUN_DIR = "$DATA_DIR/run"
    const val HISTORY_DIR = "$DATA_DIR/history"
    const val WATCHES_DIR = "$DATA_DIR/watches"
    const val WATCH_LOG_DIR = "$DATA_DIR/watch-log"
    const val CONFIG_FILE = "$DATA_DIR/config"
    const val AUTOSTART_FILE = "$DATA_DIR/autostart"
    const val ZYGISK_STATE_FILE = "$RUN_DIR/zygisk.state"
    const val MOUNT_LOG_FILE = "$RUN_DIR/mounts.log"
    const val RULE_FILE_SUFFIX = ".rule"

    const val LOCATION_DATA = 1
    const val LOCATION_CACHE = 2

    const val BACKEND_FUSE = 0
    const val BACKEND_SDCARDFS = 1
    const val BACKEND_FUSE_BPF = 2

    const val EVENT_CREATE = 1
    const val EVENT_MODIFY = 2
    const val EVENT_DELETE = 3
    const val EVENT_MOVED_FROM = 4
    const val EVENT_MOVED_TO = 5
    const val EVENT_CREATE_DIR = 6
    const val EVENT_DELETE_DIR = 7

    const val WATCH_KIND_FOLDER = 1
    const val WATCH_KIND_FILE = 2
    const val WATCH_KIND_ALL = 3

    fun isolatedRelativeRoot(packageName: String, location: Int): String =
        if (location == LOCATION_CACHE) {
            "Android/data/$packageName/cache/sdcard"
        } else {
            "Android/data/$packageName/sdcard"
        }

    fun ruleFile(userId: Int, packageName: String): String =
        "$RULES_DIR/$userId/$packageName$RULE_FILE_SUFFIX"

    fun watchFile(userId: Int): String = "$WATCHES_DIR/$userId.watch"
}
