package coursier.paths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import dev.dirs.ProjectDirectories;
import dev.dirs.impl.Windows;
import dev.dirs.jni.WindowsJni;

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
    private static volatile File priviledgedArchiveCacheDirectory0 = null;
    private static volatile File digestBasedCacheDirectory0 = null;
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

    private static String computePriviledgedArchiveCacheDirectory() throws IOException {
        return computeCacheDirectory("COURSIER_PRIVILEDGED_ARCHIVE_CACHE", "coursier.priviledged.archive.cache", "priv");
    }

    private static String computeDigestBasedCacheDirectory() throws IOException {
        return computeCacheDirectory("COURSIER_DIGEST_BASED_CACHE", "coursier.digest-based.cache", "digest");
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

    public static File priviledgedArchiveCacheDirectory() throws IOException {

        if (priviledgedArchiveCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (priviledgedArchiveCacheDirectory0 == null) {
                    priviledgedArchiveCacheDirectory0 = new File(computePriviledgedArchiveCacheDirectory()).getAbsoluteFile();
                }
            }

        return priviledgedArchiveCacheDirectory0;
    }

    public static File digestBasedCacheDirectory() throws IOException {

        if (digestBasedCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (digestBasedCacheDirectory0 == null) {
                    digestBasedCacheDirectory0 = new File(computeDigestBasedCacheDirectory()).getAbsoluteFile();
                }
            }

        return digestBasedCacheDirectory0;
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

    public static ProjectDirectories directoriesInstance(String name) {
        Supplier<Windows> windows;
        if (coursier.paths.Util.useJni())
            windows = WindowsJni.getJdkAwareSupplier();
        else
            windows = Windows.getDefaultSupplier();
        return ProjectDirectories.from(null, null, name, windows);
    }

    private static ProjectDirectories coursierDirectories() throws IOException {

        if (coursierDirectories0 == null)
            synchronized (coursierDirectoriesLock) {
                if (coursierDirectories0 == null) {
                    coursierDirectories0 = directoriesInstance("Coursier");
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

    private static Path scalaConfigFile0 = null;

    public static Path scalaConfigFile() throws Throwable {
        if (scalaConfigFile0 == null) {
            Path configPath = null;
            String fromEnv = System.getenv("SCALA_CLI_CONFIG");
            if (fromEnv != null && fromEnv.length() > 0)
                configPath = Paths.get(fromEnv);
            if (configPath == null) {
                String fromProps = System.getProperty("scala-cli.config");
                if (fromProps != null && fromProps.length() > 0)
                    configPath = Paths.get(fromProps);
            }
            if (configPath == null) {
                ProjectDirectories dirs = CoursierPaths.directoriesInstance("ScalaCli");
                configPath = Paths.get(dirs.dataLocalDir).resolve("secrets/config.json");
            }

            scalaConfigFile0 = configPath;
        }
        return scalaConfigFile0;
    }

}
