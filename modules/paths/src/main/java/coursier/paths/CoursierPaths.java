package coursier.paths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Supplier;

import dev.dirs.ProjectDirectories;
import dev.dirs.impl.Windows;
import dev.dirs.jni.WindowsJni;

/**
 * Computes Coursier's directories according to the standard
 * defined by operating system Coursier is running on.
 *
 * Note that if more paths e.g. for configuration or application data are required,
 * use {@link #coursierDirectories} and do not roll your own logic.
 */
public final class CoursierPaths {
    private CoursierPaths() {
        throw new Error();
    }

    private static final Object coursierDirectoriesLock = new Object();
    private static ProjectDirectories coursierDirectories0;

    private static final Object cacheDirectoryLock = new Object();
    private static volatile Path cacheDirectory0 = null;
    private static volatile Path archiveCacheDirectory0 = null;
    private static volatile Path priviledgedArchiveCacheDirectory0 = null;
    private static volatile Path digestBasedCacheDirectory0 = null;
    private static volatile Path jvmCacheDirectory0 = null;

    private static final Object configDirectoryLock = new Object();
    private static volatile Path[] configDirectories0 = null;

    private static final Object dataLocalDirectoryLock = new Object();
    private static volatile Path dataLocalDirectory0 = null;

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
        return computeCacheDirectoryFrom(System.getenv(envVar), System.getProperty(propName), dirName);
    }

    public static String computeCacheDirectoryFrom(String env, String prop, String dirName) throws IOException {
        if (env != null)
            return env;
        if (prop != null)
            return prop;

        Path baseXdgDir = Paths.get(coursierDirectories().cacheDir);
        Path xdgDir = baseXdgDir.resolve(dirName);

        Util.createDirectories(xdgDir);

        return xdgDir.toAbsolutePath().normalize().toString();
    }

    public static File cacheDirectory() throws IOException {
        return cacheDirectoryPath().toFile();
    }

    public static Path cacheDirectoryPath() throws IOException {

        if (cacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (cacheDirectory0 == null) {
                    cacheDirectory0 = Paths.get(computeCacheDirectory()).toAbsolutePath().normalize();
                }
            }

        return cacheDirectory0;
    }

    public static File archiveCacheDirectory() throws IOException {
        return archiveCacheDirectoryPath().toFile();
    }

    public static Path archiveCacheDirectoryPath() throws IOException {

        if (archiveCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (archiveCacheDirectory0 == null) {
                    archiveCacheDirectory0 = Paths.get(computeArchiveCacheDirectory()).toAbsolutePath().normalize();
                }
            }

        return archiveCacheDirectory0;
    }

    public static File priviledgedArchiveCacheDirectory() throws IOException {
        return priviledgedArchiveCacheDirectoryPath().toFile();
    }

    public static Path priviledgedArchiveCacheDirectoryPath() throws IOException {

        if (priviledgedArchiveCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (priviledgedArchiveCacheDirectory0 == null) {
                    priviledgedArchiveCacheDirectory0 = Paths.get(computePriviledgedArchiveCacheDirectory()).toAbsolutePath().normalize();
                }
            }

        return priviledgedArchiveCacheDirectory0;
    }

    public static File digestBasedCacheDirectory() throws IOException {
        return digestBasedCacheDirectoryPath().toFile();
    }

    public static Path digestBasedCacheDirectoryPath() throws IOException {

        if (digestBasedCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (digestBasedCacheDirectory0 == null) {
                    digestBasedCacheDirectory0 = Paths.get(computeDigestBasedCacheDirectory()).toAbsolutePath().normalize();
                }
            }

        return digestBasedCacheDirectory0;
    }

    public static File jvmCacheDirectory() throws IOException {
        return jvmCacheDirectoryPath().toFile();
    }

    public static Path jvmCacheDirectoryPath() throws IOException {

        if (jvmCacheDirectory0 == null)
            synchronized (cacheDirectoryLock) {
                if (jvmCacheDirectory0 == null) {
                    jvmCacheDirectory0 = Paths.get(computeJvmCacheDirectory()).toAbsolutePath().normalize();
                }
            }

        return jvmCacheDirectory0;
    }

    public static ProjectDirectories directoriesInstance(String name) {
        Supplier<Windows> windows;

        if (Boolean.getBoolean("coursier.windows.disable-ffm")) {
            if (coursier.paths.Util.useJni())
                windows = () -> new dev.dirs.jni.WindowsJni();
            else
                windows = () -> new dev.dirs.impl.WindowsPowerShell();
        }
        else {
            if (coursier.paths.Util.useJni())
                windows = WindowsJni.getJdkAwareSupplier();
            else
                windows = Windows.getDefaultSupplier();
        }
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

    private static Path[] computeConfigDirectories() throws IOException {
        return computeConfigDirectories(System.getenv("COURSIER_CONFIG_DIR"), System.getProperty("coursier.config-dir"));
    }
    private static Path[] computeConfigDirectories(String fromEnv, String fromProps) throws IOException {
        String path = fromEnv;

        if (path == null)
            path = fromProps;

        if (path != null)
            return new Path[] { Paths.get(path).toAbsolutePath().normalize() };

        String configDir = coursierDirectories().configDir;
        String preferenceDir = coursierDirectories().preferenceDir;
        if (configDir.equals(preferenceDir))
            return new Path[] {
                Paths.get(configDir).toAbsolutePath().normalize(),
            };
        else
            return new Path[] {
                Paths.get(configDir).toAbsolutePath().normalize(),
                Paths.get(preferenceDir).toAbsolutePath().normalize()
            };
    }

    public static File[] configDirectories() throws IOException {
        return Arrays.stream(configDirectoriesPaths()).map(Path::toFile).toArray(File[]::new);
    }

    public static File[] configDirectories(String fromEnv, String fromProps) throws IOException {
        return Arrays.stream(configDirectoriesPaths(fromEnv, fromProps)).map(Path::toFile).toArray(File[]::new);
    }

    public static Path[] configDirectoriesPaths() throws IOException {

        if (configDirectories0 == null)
            synchronized (configDirectoryLock) {
                if (configDirectories0 == null) {
                    configDirectories0 = computeConfigDirectories();
                }
            }

        return configDirectories0.clone();
    }

    public static Path[] configDirectoriesPaths(String fromEnv, String fromProps) throws IOException {
        if (fromEnv == null && fromProps == null)
            return configDirectoriesPaths();
        else
            return computeConfigDirectories(fromEnv, fromProps);
    }

    @Deprecated
    public static File configDirectory() throws IOException {
        return configDirectories()[0];
    }

    public static File defaultConfigDirectory() throws IOException {
        return configDirectories()[0];
    }

    public static Path defaultConfigDirectoryPath() throws IOException {
        return configDirectoriesPaths()[0];
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
        return dataLocalDirectoryPath().toFile();
    }

    public static Path dataLocalDirectoryPath() throws IOException {

        if (dataLocalDirectory0 == null)
            synchronized (dataLocalDirectoryLock) {
                if (dataLocalDirectory0 == null) {
                    dataLocalDirectory0 = Paths.get(computeDataLocalDirectory()).toAbsolutePath().normalize();
                }
            }

        return dataLocalDirectory0;
    }

    public static File projectCacheDirectory() throws IOException {
        return new File(coursierDirectories().cacheDir);
    }

    public static Path projectCacheDirectoryPath() throws IOException {
        return Paths.get(coursierDirectories().cacheDir);
    }

    private static Path scalaConfigFile0 = null;

    public static Path scalaConfigFile(String fromEnv, String fromProps) throws Throwable {
        Path configPath = null;
        if (fromEnv != null && fromEnv.length() > 0)
            configPath = Paths.get(fromEnv);
        if (configPath == null && (fromProps != null && fromProps.length() > 0))
            configPath = Paths.get(fromProps);
        if (configPath == null) {
            ProjectDirectories dirs = CoursierPaths.directoriesInstance("ScalaCli");
            configPath = Paths.get(dirs.dataLocalDir).resolve("secrets/config.json");
        }
        return configPath;
    }

    public static Path scalaConfigFile() throws Throwable {
        if (scalaConfigFile0 == null)
            scalaConfigFile0 = scalaConfigFile(System.getenv("SCALA_CLI_CONFIG"), System.getProperty("scala-cli.config"));
        return scalaConfigFile0;
    }

}
