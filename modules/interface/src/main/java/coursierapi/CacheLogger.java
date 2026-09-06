package coursierapi;

public interface CacheLogger extends Logger {
    default void foundLocally(String url) {}

    default void checkingArtifact(String url, Artifact artifact) {}

    // We may have artifact.url != url. In that case, url should be the URL of a checksum of artifact.
    default void downloadingArtifact(String url, Artifact artifact) {}

    default void downloadProgress(String url, long downloaded) {}

    default void downloadedArtifact(String url, boolean success) {}
    default void checkingUpdates(String url, Long currentTimeOpt) {}
    default void checkingUpdatesResult(
            String url,
            Long currentTimeOpt,
            Long remoteTimeOpt
    ) {}

    default void downloadLength(
            String url,
            long totalLength,
            long alreadyDownloaded,
            boolean watching
    ) {}

    default void gettingLength(String url) {}
    default void gettingLengthResult(String url, Long length) {}

    default void removedCorruptFile(String url, String reason) {}

    default void pickedModuleVersion(String module, String version) {}

    // sizeHint: estimated # of artifacts to be downloaded (doesn't include side stuff like checksums)
    default void init(Integer sizeHint) {}
    default void stop() {}
}
