package coursier.paths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import dev.dirs.GetWinDirs;
import dev.dirs.ProjectDirectories;

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
    private static ProjectDirectories coursierDirectories0;

    private static final Object cacheDirectoryLock = new Object();
    private static volatile File cacheDirectory0 = null;
    private static volatile File archiveCacheDirectory0 = null;
    private static volatile File jvmCacheDirectory0 = null;

    private static final Object configDirectoryLock = new Object();
    private static volatile File[] configDirectories0 = null;

    private static final Object dataLocalDirectoryLock = new Object();
    private static volatile File dataLocalDirectory0 = null;

    // TODO After switching to nio, that logic can be unit tested with mock filesystems.

    private static String computeCacheDirectory() throws IOException {
        return computeCacheDirectory("COURSIER_CACHE", "coursier.cache", "v1");
    }

    private static String computeArchiveCacheDirectory() throws IOException {
        return computeCacheDirectory("COURSIER_ARCHIVE_CACHE", "coursier.archive.cache", "arc");
    }

    private static String computeJvmCacheDirectory() throws IOException {
        return computeCacheDirectory("COURSIER_JVM_CACHE", "coursier.jvm.cache", "jvm");
    }

    private static String computeCacheDirectory(String envVar, String propName, String dirName) throws IOException {
        String path = System.getenv(envVar);

        if (path == null)
            path = System.getProperty(propName);

        if (path != null)
          return path;

        File baseXdgDir = new File(coursierDirectories().cacheDir);
        File xdgDir = new File(baseXdgDir, dirName);

        Util.createDirectories(xdgDir.toPath());

        return xdgDir.getAbsolutePath();
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

    public static File archiveCacheDirectory() throws IOException {

        if (archiveCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (archiveCacheDirectory0 == null) {
                    archiveCacheDirectory0 = new File(computeArchiveCacheDirectory()).getAbsoluteFile();
                }
            }

        return archiveCacheDirectory0;
    }

    public static File jvmCacheDirectory() throws IOException {

        if (jvmCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (jvmCacheDirectory0 == null) {
                    jvmCacheDirectory0 = new File(computeJvmCacheDirectory()).getAbsoluteFile();
                }
            }

        return jvmCacheDirectory0;
    }

    private static ProjectDirectories coursierDirectories() throws IOException {

        if (coursierDirectories0 == null)
            synchronized (coursierDirectoriesLock) {
                if (coursierDirectories0 == null) {
                    GetWinDirs getWinDirs;
                    if (coursier.paths.Util.useJni())
                        getWinDirs = guids -> {
                            String[] dirs = new String[guids.length];
                            for (int i = 0; i < guids.length; i++) {
                                dirs[i] = coursier.jniutils.WindowsKnownFolders.knownFolderPath("{" + guids[i] + "}");
                            }
                            return dirs;
                        };
                    else
                        getWinDirs = GetWinDirs.powerShellBased;
                    coursierDirectories0 = ProjectDirectories.from(null, null, "Coursier", getWinDirs);
                }
            }

        return coursierDirectories0;
    }

    private static File[] computeConfigDirectories() throws IOException {
        String path = System.getenv("COURSIER_CONFIG_DIR");

        if (path == null)
            path = System.getProperty("coursier.config-dir");

        if (path != null)
            return new File[] { new File(path).getAbsoluteFile() };

        String configDir = coursierDirectories().configDir;
        String preferenceDir = coursierDirectories().preferenceDir;
        if (configDir.equals(preferenceDir))
            return new File[] {
                new File(configDir).getAbsoluteFile(),
            };
        else
            return new File[] {
                new File(configDir).getAbsoluteFile(),
                new File(preferenceDir).getAbsoluteFile()
            };
    }

    public static File[] configDirectories() throws IOException {

        if (configDirectories0 == null)
            synchronized (configDirectoryLock) {
                if (configDirectories0 == null) {
                    configDirectories0 = computeConfigDirectories();
                }
            }

        return configDirectories0.clone();
    }

    @Deprecated
    public static File configDirectory() throws IOException {
        return configDirectories()[0];
    }

    public static File defaultConfigDirectory() throws IOException {
        return configDirectories()[0];
    }

    private static String computeDataLocalDirectory() throws IOException {
        String path = System.getenv("COURSIER_DATA_DIR");

        if (path == null)
            path = System.getProperty("coursier.data-dir");

        if (path != null)
          return path;

        return coursierDirectories().dataLocalDir;
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

    public static File projectCacheDirectory() throws IOException {
        return new File(coursierDirectories().cacheDir);
    }
}
