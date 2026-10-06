package killua.dev.cleaner.ipc;

import killua.dev.cleaner.ipc.FileEntryParcel;
import killua.dev.cleaner.ipc.FileEventParcel;
import killua.dev.cleaner.ipc.IFileEventListener;
import killua.dev.cleaner.ipc.MountRecordParcel;
import killua.dev.cleaner.ipc.ServerStatusParcel;
import killua.dev.cleaner.ipc.WatchEventParcel;

interface IIsolationDaemon {

    int protocolRevision() = 1;

    ServerStatusParcel getStatus() = 2;

    // isolation

    List<MountRecordParcel> getMountRecords(int limit) = 14;

    // isolated storage

    List<FileEntryParcel> listIsolated(String packageName, int userId, String subPath) = 20;

    List<FileEntryParcel> analyzeIsolated(String packageName, int userId) = 21;

    List<FileEntryParcel> listIsolatedMedia(String packageName, int userId, int limit) = 22;

    ParcelFileDescriptor openIsolated(String packageName, int userId, String subPath) = 23;

    List<String> listRealFolders(int userId, String subPath) = 25;

    // file monitor

    void setMonitorEnabled(boolean enabled) = 30;

    List<FileEventParcel> queryFileEvents(String packageName, int userId, int limit) = 31;

    void clearFileEvents(String packageName, int userId) = 32;

    void registerFileEventListener(IFileEventListener listener) = 33;

    void unregisterFileEventListener(IFileEventListener listener) = 34;

    void setAutoStart(boolean enabled) = 35;

    List<FileEventParcel> latestFileEvents(int userId) = 36;

    void reloadWatches(int userId) = 40;

    List<WatchEventParcel> queryWatchEvents(String ruleId, int userId, int limit) = 41;

    void clearWatchEvents(String ruleId, int userId) = 42;

    List<String> pendingWatches(int userId) = 43;

    void exit() = 100;
}
