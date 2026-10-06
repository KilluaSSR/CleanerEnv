package killua.dev.cleaner.ipc;

import killua.dev.cleaner.ipc.FileEventParcel;
import killua.dev.cleaner.ipc.WatchEventParcel;

oneway interface IFileEventListener {
    void onFileEvent(in FileEventParcel event);

    void onWatchEvent(in WatchEventParcel event);
}
