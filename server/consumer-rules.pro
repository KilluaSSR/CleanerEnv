# Nothing in the app references the server: cleaner-starter execs
# `app_process ... killua.dev.cleaner.server.ServerMain` with the APK on CLASSPATH
# (IpcConstants.SERVER_MAIN_CLASS / SERVER_MAIN_CLASS in native/cleaner-starter).
# Everything else the server runs is reachable from main.
-keep class killua.dev.cleaner.server.ServerMain {
    public static void main(java.lang.String[]);
}
