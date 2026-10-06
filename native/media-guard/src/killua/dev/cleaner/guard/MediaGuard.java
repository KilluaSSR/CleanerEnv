package killua.dev.cleaner.guard;

import android.util.Log;

public final class MediaGuard {
    /** Shared with the Zygisk module's native log, so one tag covers the whole load path. */
    static final String TAG = "CleanerIsolation";

    private MediaGuard() {
    }

    public static void arm(String policyFile) {
        try {
            PolicyFeed feed = PolicyFeed.start(policyFile);
            PublishHook.install(feed);
            Log.i(TAG, "media guard: armed, policy " + policyFile);
        } catch (Throwable failure) {
            Log.e(TAG, "media guard: arming failed, MediaProvider stays unguarded", failure);
        }
    }
}
