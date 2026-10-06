package killua.dev.cleaner.guard;

import android.os.FileObserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

final class PolicyFeed implements Runnable {
    private static final int MAX_LENGTH = 4 << 20;
    private static final long POLL_MS = 10_000L;

    private final File file;
    private final Object signal = new Object();
    private volatile Map<Integer, ProviderPolicy> byUid = Collections.emptyMap();
    private long loadedModified = -1L;
    private long loadedLength = -1L;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private FileObserver observer;

    private PolicyFeed(File file) {
        this.file = file;
    }

    static PolicyFeed start(String path) {
        PolicyFeed feed = new PolicyFeed(new File(path));
        feed.reload();
        feed.watch();
        Thread reader = new Thread(feed, "cleaner-media-guard");
        reader.setDaemon(true);
        reader.start();
        return feed;
    }

    ProviderPolicy forUid(int uid) {
        return byUid.get(uid);
    }

    private void watch() {
        File dir = file.getParentFile();
        if (dir == null) {
            return;
        }
        final String name = file.getName();
        observer = new FileObserver(dir, FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String path) {
                if (name.equals(path)) {
                    synchronized (signal) {
                        signal.notifyAll();
                    }
                }
            }
        };
        observer.startWatching();
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized (signal) {
                    signal.wait(POLL_MS);
                }
            } catch (InterruptedException stop) {
                return;
            }
            reload();
        }
    }
    private void reload() {
        long modified = file.lastModified();
        long length = file.length();
        if (modified == 0L || (modified == loadedModified && length == loadedLength)) {
            return;
        }
        if (length > MAX_LENGTH) {
            return;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] body = new byte[(int) length];
            int filled = 0;
            while (filled < body.length) {
                int read = in.read(body, filled, body.length - filled);
                if (read < 0) {
                    break;
                }
                filled += read;
            }
            byUid = ProviderPolicy.decode(new String(body, 0, filled, StandardCharsets.UTF_8));
            loadedModified = modified;
            loadedLength = length;
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
