package coursierapi;

public interface SimpleLogger extends Logger {
    default void starting(String url) {}
    default void length(String url, long total, long alreadyDownloaded, boolean watching) {}
    default void progress(String url, long downloaded) {}
    default void done(String url, boolean success) {}
}
