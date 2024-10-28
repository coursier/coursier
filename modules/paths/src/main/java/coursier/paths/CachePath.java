package coursier.paths;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache paths logic, shared by the cache and bootstrap modules
 */
public class CachePath {

    private static int maxStructureLockAttemptCount = Integer.getInteger("coursier.structure-lock-retry-count", 5);
    private static long structureLockInitialRetryDelay = Long.getLong("coursier.structure-lock-retry-initial-delay-ms", 10L);
    private static double structureLockRetryDelayMultiplier = Double.parseDouble(System.getProperty("coursier.structure-lock-retry-multiplier", "2.0"));

    private static boolean throwExceptions = true;

    // based on https://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
    // '/' was removed from the unsafe list
    private static String escape(String input) {
        StringBuilder resultStr = new StringBuilder();
        for (char ch : input.toCharArray()) {
            if (isUnsafe(ch)) {
                resultStr.append('%');
                resultStr.append(toHex(ch / 16));
                resultStr.append(toHex(ch % 16));
            } else {
                resultStr.append(ch);
            }
        }
        return resultStr.toString();
    }

    private static char toHex(int ch) {
        return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
    }

    private static boolean isUnsafe(char ch) {
        return ch > 128 || " %$&+,:;=?@<>#%".indexOf(ch) >= 0;
    }

    public static File localFile(String url, File cache, String user, boolean localArtifactsShouldBeCached) throws MalformedURLException {

        if (url.startsWith("file:/") && !localArtifactsShouldBeCached) {
            try {
                return Paths.get(new URI(url)).toFile();
            } catch (URISyntaxException e) {
                // Legacy way of converting URL to files in coursier
                // Maybe we should just throw e instead…
                if (url.startsWith("file:///"))
                    return new File(url.substring("file://".length()));

                return new File(url.substring("file:".length()));
            }
        }

        String[] split = url.split(":", 2);
        if (split.length != 2)
            throw new MalformedURLException("No protocol found in URL " + url);

        String protocol = split[0];
        String remaining = split[1];

        if (remaining.startsWith("///"))
            remaining = remaining.substring("///".length());
        else if (remaining.startsWith("/"))
            remaining = remaining.substring("/".length());
        else
            throw new MalformedURLException("URL " + url + " doesn't contain an absolute path");

        if (remaining.endsWith("/"))
            // keeping directory content in .directory files
            remaining = remaining + ".directory";

        while (remaining.startsWith("/"))
            remaining = remaining.substring(1);

        String userPart = "";
        if (user != null)
            userPart = user + "@";

        Path localPath = cache.toPath().normalize().resolve(escape(protocol + "/" + userPart + remaining));
        if (!localPath.normalize().equals(localPath)) {
          throw new IllegalArgumentException(url + " contains at least one redundant path element");
        }

        return localPath.toFile();
    }

    public static File temporaryFile(File file) {
        File dir = file.getParentFile();
        String name = file.getName();
        return new File(dir, "." + name + ".part");
    }

    public static Path lockFile(Path path) {
        return path.getParent().resolve(path.getFileName().toString() + ".lock");
    }

    public static File lockFile(File file) {
        return new File(file.getParentFile(), file.getName() + ".lock");
    }

    public static File defaultCacheDirectory() throws IOException {
        return CoursierPaths.cacheDirectory();
    }

    public static File defaultArchiveCacheDirectory() throws IOException {
        return CoursierPaths.archiveCacheDirectory();
    }

    // Trying to limit the calls to String.intern via this map (https://shipilev.net/jvm/anatomy-quarks/10-string-intern/)
    private static ConcurrentHashMap<String, Object> internedStrings = new ConcurrentHashMap<>();

    // Even if two versions of that code end up in the same JVM (say one via a shaded coursier, the other via a
    // non-shaded coursier), they will rely on the exact same object for locking here (via String.intern), so that the
    // locks are actually JVM-wide.
    private static Object lockFor(Path cachePath) {
        String key = "coursier-jvm-lock-" + cachePath.toString();
        Object lock0 = internedStrings.get(key);
        if (lock0 == null) {
            String internedKey = key.intern();
            internedStrings.putIfAbsent(internedKey, internedKey);
            lock0 = internedKey;
        }
        return lock0;
    }

    private static class StructureLockException extends Exception {
        public StructureLockException(Exception parent) {
            super(parent);
        }
    }

    public static <V> V withStructureLock(File cache, Callable<V> callable) throws Exception {

        int attemptCount = 1;
        long retryDelay = structureLockInitialRetryDelay;
        while (attemptCount < maxStructureLockAttemptCount) {
            attemptCount = attemptCount + 1;

            try {
                return withStructureLockOnce(cache, callable);
            }
            catch (StructureLockException ex) {

            }

            Thread.sleep(retryDelay);
            retryDelay = (long) (structureLockRetryDelayMultiplier * retryDelay);
        }

        return withStructureLockOnce(cache, callable);
    }

    private static <V> V withStructureLockOnce(File cache, Callable<V> callable) throws Exception {

        // Should really be
        //   return withStructureLock(cache.toPath(), callable);
        // Keeping the former java.io based implem for now, as this is some quite sensitive code.

        Path cachePath = cache.toPath();
        Object intraProcessLock = lockFor(cachePath);

        synchronized (intraProcessLock) {
            File lockFile = new File(cache, ".structure.lock");
            Util.createDirectories(lockFile.toPath().getParent());
            FileChannel channel = null;

            try {
                try {
                    channel = FileChannel.open(
                        lockFile.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.DELETE_ON_CLOSE);
                } catch (FileNotFoundException ex) {
                    throw throwExceptions ? ex : new StructureLockException(ex);
                }

                FileLock lock = null;
                try {
                    try {
                        lock = channel.lock();
                    } catch (FileNotFoundException ex) {
                        throw throwExceptions ? ex : new StructureLockException(ex);
                    }

                    try {
                        return callable.call();
                    }
                    finally {
                        lock.release();
                        lock = null;
                        channel.close();
                        channel = null;
                    }
                }
                finally {
                    if (lock != null) lock.release();
                }
            } finally {
                if (channel != null) channel.close();
            }
        }
    }

    public static <V> V withStructureLock(Path cache, Callable<V> callable) throws Exception {

        int attemptCount = 1;
        long retryDelay = structureLockInitialRetryDelay;
        while (attemptCount < maxStructureLockAttemptCount) {
            attemptCount = attemptCount + 1;

            try {
                return withStructureLockOnce(cache, callable);
            }
            catch (StructureLockException ex) {

            }

            Thread.sleep(retryDelay);
            retryDelay = (long) (structureLockRetryDelayMultiplier * retryDelay);
        }

        return withStructureLockOnce(cache, callable);
    }

    private static <V> V withStructureLockOnce(Path cache, Callable<V> callable) throws Exception {

        Object intraProcessLock = lockFor(cache);

        synchronized (intraProcessLock) {
            Path lockFile = cache.resolve(".structure.lock");
            Util.createDirectories(lockFile.getParent());
            FileChannel channel = null;

            try {

                try {
                    channel = FileChannel.open(
                            lockFile,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.DELETE_ON_CLOSE
                    );
                } catch (FileNotFoundException ex) {
                    throw throwExceptions ? ex : new StructureLockException(ex);
                }

                FileLock lock = null;
                try {
                    try {
                        lock = channel.lock();
                    } catch (FileNotFoundException ex) {
                        throw throwExceptions ? ex : new StructureLockException(ex);
                    }

                    try {
                        return callable.call();
                    }
                    finally {
                        lock.release();
                        lock = null;
                        channel.close();
                        channel = null;
                    }
                }
                finally {
                    if (lock != null) lock.release();
                }
            } finally {
                if (channel != null) channel.close();
            }
        }
    }

}
