package coursier.paths;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache paths logic, shared by the cache and bootstrap modules
 */
public class CachePath {

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

        // use the File constructor accepting a URI in case of problem with the two cases below?

        if (url.startsWith("file:///") && !localArtifactsShouldBeCached)
            return new File(url.substring("file://".length()));

        if (url.startsWith("file:/") && !localArtifactsShouldBeCached)
            return new File(url.substring("file:".length()));

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

        return new File(cache, escape(protocol + "/" + userPart + remaining));
    }

    public static File temporaryFile(File file) {
        File dir = file.getParentFile();
        String name = file.getName();
        return new File(dir, name + ".part");
    }

    public static File lockFile(File file) {
        return new File(file.getParentFile(), file.getName() + ".lock");
    }

    public static File defaultCacheDirectory() throws IOException {
        return CoursierPaths.cacheDirectory();
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

    public static <V> V withStructureLock(File cache, Callable<V> callable) throws Exception {

        // Should really be
        //   return withStructureLock(cache.toPath(), callable);
        // Keeping the former java.io based implem for now, as this is some quite sensitive code.

        Path cachePath = cache.toPath();
        Object intraProcessLock = lockFor(cachePath);

        synchronized (intraProcessLock) {
            File lockFile = new File(cache, ".structure.lock");
            Files.createDirectories(lockFile.toPath().getParent());
            FileOutputStream out = null;

            try {
                out = new FileOutputStream(lockFile);

                FileLock lock = null;
                try {
                    lock = out.getChannel().lock();

                    try {
                        return callable.call();
                    }
                    finally {
                        lock.release();
                        lock = null;
                        out.close();
                        out = null;
                        lockFile.delete();
                    }
                }
                finally {
                    if (lock != null) lock.release();
                }
            } finally {
                if (out != null) out.close();
            }
        }
    }

    public static <V> V withStructureLock(Path cache, Callable<V> callable) throws Exception {

        Object intraProcessLock = lockFor(cache);

        synchronized (intraProcessLock) {
            Path lockFile = cache.resolve(".structure.lock");
            Files.createDirectories(lockFile.getParent());
            FileChannel channel = null;

            try {

                channel = FileChannel.open(
                        lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.DELETE_ON_CLOSE
                );

                FileLock lock = null;
                try {
                    lock = channel.lock();

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
