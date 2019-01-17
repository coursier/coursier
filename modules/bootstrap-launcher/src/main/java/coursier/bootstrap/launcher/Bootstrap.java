package coursier.bootstrap.launcher;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coursier.paths.CachePath;

public class Bootstrap {

    private static void exit(String message) {
        System.err.println(message);
        System.exit(255);
    }

    private static byte[] readFullySync(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[16384];

        int nRead = is.read(data, 0, data.length);
        while (nRead != -1) {
            buffer.write(data, 0, nRead);
            nRead = is.read(data, 0, data.length);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    private final static String resourceDir = "coursier/bootstrap/launcher/";
    private final static String jarDir = resourceDir + "jars/";

    private final static String defaultURLResource = resourceDir + "bootstrap-jar-urls";
    private final static String defaultJarResource = resourceDir + "bootstrap-jar-resources";

    private static String[] readStringSequence(String resource) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            return new String[] {};
        byte[] rawContent = readFullySync(is);
        String content = new String(rawContent, StandardCharsets.UTF_8);
        if (content.length() == 0)
            return new String[] {};
        return content.split("\n");
    }

    private static String readString(String resource) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            return null;
        byte[] rawContent = readFullySync(is);
        return new String(rawContent, StandardCharsets.UTF_8);
    }

    private static ClassLoader readBaseLoaders(File cacheDir, ClassLoader baseLoader, BootstrapURLStreamHandlerFactory factory, ClassLoader loader) throws IOException {

        ClassLoader parentLoader = baseLoader;
        int i = 1;
        while (true) {
            String[] strUrls = readStringSequence(resourceDir + "bootstrap-jar-urls-" + i);
            String[] resources = readStringSequence(resourceDir + "bootstrap-jar-resources-" + i);
            String nameOrNull = readString(resourceDir + "bootstrap-loader-name-" + i);
            String[] names;
            if (nameOrNull == null)
                names = new String[]{};
            else
                names = new String[]{ nameOrNull };

            if (strUrls.length == 0 && resources.length == 0 && nameOrNull == null)
                break;

            List<URL> urls = getURLs(strUrls, resources, factory, loader);
            List<URL> localURLs = getLocalURLs(urls, cacheDir, factory);

            parentLoader = new IsolatedClassLoader(localURLs.toArray(new URL[0]), parentLoader, names);

            i = i + 1;
        }

        return parentLoader;
    }

    private final static int concurrentDownloadCount;

    static {
        String prop = System.getProperty("coursier.parallel-download-count");
        if (prop == null)
            concurrentDownloadCount = 6;
        else
            concurrentDownloadCount = Integer.parseUnsignedInt(prop);
    }

    // http://stackoverflow.com/questions/872272/how-to-reference-another-property-in-java-util-properties/27724276#27724276
    private static Map<String,String> loadPropertiesMap(InputStream s) throws IOException {
        final Map<String, String> ordered = new LinkedHashMap<>();
        //Hack to use properties class to parse but our map for preserved order
        Properties bp = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                ordered.put((String)key, (String)value);
                return super.put(key, value);
            }
        };
        bp.load(s);

        final Pattern propertyRegex = Pattern.compile(Pattern.quote("${") + "[^" + Pattern.quote("{[()]}") + "]*" + Pattern.quote("}"));

        final Map<String, String> resolved = new LinkedHashMap<>(ordered.size());

        for (String k : ordered.keySet()) {
            String value = ordered.get(k);

            Matcher matcher = propertyRegex.matcher(value);

            // cycles would loop indefinitely here :-|
            while (matcher.find()) {
                int start = matcher.start(0);
                int end = matcher.end(0);
                String subKey = value.substring(start + 2, end - 1);
                String subValue = resolved.get(subKey);
                if (subValue == null)
                    subValue = System.getProperty(subKey);
                value = value.substring(0, start) + subValue + value.substring(end);
            }

            resolved.put(k, value);
        }
        return resolved;
    }

    private static String mainJarPath() {
        ProtectionDomain protectionDomain = Bootstrap.class.getProtectionDomain();
        if (protectionDomain != null) {
            CodeSource source = protectionDomain.getCodeSource();
            if (source != null) {
                URL location = source.getLocation();
                if (location != null && location.getProtocol().equals("file")) {
                    return location.getPath();
                }
            }
        }

        return "";
    }

    // from http://www.java2s.com/Code/Java/File-Input-Output/Readfiletobytearrayandsavebytearraytofile.htm
    private static void writeBytesToFile(File file, byte[] bytes) throws IOException {
        BufferedOutputStream bos = null;

        try {
            FileOutputStream fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bytes);
        } finally {
            if (bos != null) {
                try  {
                    // flush and close the BufferedOutputStream
                    bos.flush();
                    bos.close();
                } catch (Exception e) {}
            }
        }
    }

    private static List<URL> getLocalURLs(List<URL> urls, final File cacheDir, BootstrapURLStreamHandlerFactory factory) throws MalformedURLException {

        ThreadFactory threadFactory = new ThreadFactory() {
            // from scalaz Strategy.DefaultDaemonThreadFactory
            ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
            public Thread newThread(Runnable r) {
                Thread t = defaultThreadFactory.newThread(r);
                t.setDaemon(true);
                return t;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(concurrentDownloadCount, threadFactory);

        CompletionService<URL> completionService =
                new ExecutorCompletionService<>(pool);

        List<URL> localURLs = new ArrayList<>();
        List<URL> missingURLs = new ArrayList<>();

        for (URL url : urls) {

            String protocol = url.getProtocol();

            if (protocol.equals("file") || protocol.equals(factory.getProtocol())) {
                localURLs.add(url);
            } else {
                // fourth argument is false because we don't want to store local files when bootstrapping
                File dest = CachePath.localFile(url.toString(), cacheDir, null, false);

                if (dest.exists()) {
                    localURLs.add(dest.toURI().toURL());
                } else {
                    missingURLs.add(url);
                }
            }
        }

        for (final URL url : missingURLs) {
            completionService.submit(() -> {
                // fourth argument is false because we don't want to store local files when bootstrapping
                final File dest = CachePath.localFile(url.toString(), cacheDir, null, false);

                if (!dest.exists()) {
                    FileOutputStream out = null;
                    FileLock lock = null;

                    final File tmpDest = CachePath.temporaryFile(dest);
                    final File lockFile = CachePath.lockFile(tmpDest);

                    try {

                        out = CachePath.withStructureLock(cacheDir, () -> {
                            tmpDest.getParentFile().mkdirs();
                            lockFile.getParentFile().mkdirs();
                            dest.getParentFile().mkdirs();

                            return new FileOutputStream(lockFile);
                        });

                        try {
                            lock = out.getChannel().tryLock();
                            if (lock == null)
                                throw new RuntimeException("Ongoing concurrent download for " + url);
                            else
                                try {
                                    URLConnection conn = url.openConnection();
                                    long lastModified = conn.getLastModified();
                                    InputStream s = conn.getInputStream();
                                    byte[] b = readFullySync(s);
                                    tmpDest.deleteOnExit();
                                    writeBytesToFile(tmpDest, b);
                                    tmpDest.setLastModified(lastModified);
                                    Files.move(tmpDest.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
                                }
                                finally {
                                    lock.release();
                                    lock = null;
                                    out.close();
                                    out = null;
                                    lockFile.delete();
                                }
                        }
                        catch (OverlappingFileLockException e) {
                            throw new RuntimeException("Ongoing concurrent download for " + url);
                        }
                        finally {
                            if (lock != null) lock.release();
                        }
                    } catch (Exception e) {
                        System.err.println("Error while downloading " + url + ": " + e.getMessage() + ", ignoring it");
                        throw e;
                    }
                    finally {
                        if (out != null) out.close();
                    }
                }

                return dest.toURI().toURL();
            });
        }

        String clearLine = "\033[2K";

        try {
            while (localURLs.size() < urls.size()) {
                Future<URL> future = completionService.take();
                try {
                    URL url = future.get();
                    localURLs.add(url);
                    int nowMissing = urls.size() - localURLs.size();
                    String up = "\033[1A";
                    System.err.print(clearLine + "Downloaded " + (missingURLs.size() - nowMissing) + " missing file(s) / " + missingURLs.size() + "\n" + up);
                } catch (ExecutionException ex) {
                    // Error message already printed from the Callable above
                    System.exit(255);
                }
            }
        } catch (InterruptedException ex) {
            exit("Interrupted");
        }

        if (!missingURLs.isEmpty()) {
            System.err.print(clearLine);
        }

        return localURLs;
    }

    private static void setMainProperties(String mainJarPath, String[] args) {
        System.setProperty("coursier.mainJar", mainJarPath);

        for (int i = 0; i < args.length; i++) {
            System.setProperty("coursier.main.arg-" + i, args[i]);
        }
    }

    private static void setExtraProperties() throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        String resource = resourceDir + "bootstrap.properties";
        Map<String,String> properties = loadPropertiesMap(loader.getResourceAsStream(resource));
        for (Map.Entry<String, String> ent : properties.entrySet()) {
            System.setProperty(ent.getKey(), ent.getValue());
        }
    }

    private static List<URL> getURLs(String[] rawURLs, String[] resources, BootstrapURLStreamHandlerFactory factory, ClassLoader loader) throws MalformedURLException {

        List<String> errors = new ArrayList<>();
        List<URL> urls = new ArrayList<>();

        for (String urlStr : rawURLs) {
            try {
                URL url = URI.create(urlStr).toURL();
                urls.add(url);
            } catch (Exception ex) {
                String message = urlStr + ": " + ex.getMessage();
                errors.add(message);
            }
        }

        for (String resource : resources) {
            URL url = loader.getResource(jarDir + resource);
            if (url == null) {
                String message = "Resource " + resource + " not found";
                errors.add(message);
            } else {
                URL url0 = new URL(
                        factory.getProtocol(),
                        null,
                        -1,
                        resource,
                        factory.createURLStreamHandler(factory.getProtocol()));
                urls.add(url0);
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder("Error:");
            for (String error: errors) {
                builder.append("\n  ");
                builder.append(error);
            }
            exit(builder.toString());
        }

        return urls;
    }

    public static void main(String[] args) throws Throwable {

        setMainProperties(mainJarPath(), args);
        setExtraProperties();

        String mainClass0 = System.getProperty("bootstrap.mainClass");

        File cacheDir = CachePath.defaultCacheDirectory();

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();

        BootstrapURLStreamHandlerFactory factory = new BootstrapURLStreamHandlerFactory(jarDir, contextLoader);

        String[] strUrls = readStringSequence(defaultURLResource);
        String[] resources = readStringSequence(defaultJarResource);
        List<URL> urls = getURLs(strUrls, resources, factory, contextLoader);
        List<URL> localURLs = getLocalURLs(urls, cacheDir, factory);

        Thread thread = Thread.currentThread();
        ClassLoader parentClassLoader = thread.getContextClassLoader();
        parentClassLoader = readBaseLoaders(cacheDir, parentClassLoader, factory, contextLoader);

        ClassLoader classLoader = new URLClassLoader(localURLs.toArray(new URL[0]), parentClassLoader);

        Class<?> mainClass = null;
        Method mainMethod = null;

        try {
            mainClass = classLoader.loadClass(mainClass0);
        } catch (ClassNotFoundException ex) {
            exit("Error: class " + mainClass0 + " not found");
        }

        try {
            Class[] params = { String[].class };
            mainMethod = mainClass.getMethod("main", params);
        }
        catch (NoSuchMethodException ex) {
            exit("Error: main method not found in class " + mainClass0);
        }

        thread.setContextClassLoader(classLoader);
        try {
            Object[] mainArgs = { args };
            mainMethod.invoke(null, mainArgs);
        }
        catch (IllegalAccessException ex) {
            exit(ex.getMessage());
        }
        catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
        finally {
            thread.setContextClassLoader(parentClassLoader);
        }
    }

}
