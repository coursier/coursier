package coursier.paths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Computes Coursier's directories according to the standard
 * defined by operating system Coursier is running on.
 *
 * @implNote If more paths e. g. for configuration or application data is required,
 * use {@link #coursierDirectories} and do not roll your own logic.
 */
public final class CoursierPaths {
    private CoursierPaths() {
        throw new Error();
    }

    private static final Object coursierDirectoriesLock = new Object();

    private static final Object cacheDirectoryLock = new Object();
    private static volatile File cacheDirectory0 = null;

    private static final Object configDirectoryLock = new Object();
    private static volatile File configDirectory0 = null;

    private static final Object dataLocalDirectoryLock = new Object();
    private static volatile File dataLocalDirectory0 = null;

    // TODO After switching to nio, that logic can be unit tested with mock filesystems.

    private static String computeCacheDirectory() throws IOException {
        String path = System.getenv("COURSIER_CACHE");

        if (path == null)
            path = System.getProperty("coursier.cache");

        if (path != null)
          return path;

        String xdgCache = System.getenv("XDG_CACHE_HOME");
        Path defaultCacheDir = Paths.get(System.getProperty("user.home")).resolve(".cache");
        Path baseCacheDir = xdgCache == null? defaultCacheDir: Paths.get(xdgCache);
        Path cacheDir = baseCacheDir.resolve("coursier").resolve("v1").toAbsolutePath();
        path = cacheDir.toString();
        Util.createDirectories(cacheDir);
        return path;
    }

    public static File cacheDirectory() throws IOException {

        if (cacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (cacheDirectory0 == null) {
                    cacheDirectory0 = new File(computeCacheDirectory()).getAbsoluteFile();
                }
            }

        return cacheDirectory0;
    }

    private static String computeConfigDirectory() throws IOException {
        String path = System.getenv("COURSIER_CONFIG_DIR");

        if (path == null)
            path = System.getProperty("coursier.config-dir");

        if (path != null)
          return path;

        String xdgConfig = System.getenv("XDG_CONFIG_HOME");
        Path defaultConfigDir = Paths.get(System.getProperty("user.home")).resolve(".config");
        Path baseConfigDir = xdgConfig == null? defaultConfigDir: Paths.get(xdgConfig);
        Path configDir = baseConfigDir.resolve("coursier").toAbsolutePath();
        return configDir.toString();
    }

    public static File configDirectory() throws IOException {

        if (configDirectory0 == null)
            synchronized (configDirectoryLock) {
                if (configDirectory0 == null) {
                    configDirectory0 = new File(computeConfigDirectory()).getAbsoluteFile();
                }
            }

        return configDirectory0;
    }

    private static String computeDataLocalDirectory() throws IOException {
        String path = System.getenv("COURSIER_DATA_DIR");

        if (path == null)
            path = System.getProperty("coursier.data-dir");

        if (path != null)
          return path;

        String xdgData = System.getenv("XDG_DATA_HOME");
        Path defaultDataDir = Paths.get(System.getProperty("user.home")).resolve(".local").resolve("share");
        Path baseDataDir = xdgData == null? defaultDataDir: Paths.get(xdgData);
        Path dataDir = baseDataDir.resolve("coursier").toAbsolutePath();
        return dataDir.toString();
    }

    public static File dataLocalDirectory() throws IOException {

        if (dataLocalDirectory0 == null)
            synchronized (dataLocalDirectoryLock) {
                if (dataLocalDirectory0 == null) {
                    dataLocalDirectory0 = new File(computeDataLocalDirectory()).getAbsoluteFile();
                }
            }

        return dataLocalDirectory0;
    }
}
