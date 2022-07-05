package coursier.bootstrap.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import coursier.bootstrap.launcher.credentials.Credentials;
import coursier.bootstrap.launcher.credentials.DirectCredentials;
import coursier.paths.CachePath;

class Download {

    private final int concurrentDownloadCount;
    private final File cacheDir;
    private final List<DirectCredentials> directCredentials;

    Download(int concurrentDownloadCount, File cacheDir, List<DirectCredentials> directCredentials) {
        this.concurrentDownloadCount = concurrentDownloadCount;
        this.cacheDir = cacheDir;
        this.directCredentials = Collections.unmodifiableList(directCredentials);
    }

    static Download getDefault() {
        int concurrentDownloadCount;
        File cacheDir;
        List<DirectCredentials> directCredentials;
        String prop = System.getProperty("coursier.parallel-download-count");
        if (prop == null)
            concurrentDownloadCount = 6;
        else
            concurrentDownloadCount = Integer.parseUnsignedInt(prop);
        try {
            cacheDir = CachePath.defaultCacheDirectory();
        } catch (IOException ex) {
            throw new RuntimeException("Error creating cache directory", ex);
        }
        try {
            directCredentials = Credentials.credentials().stream()
                .flatMap(credentials -> {
                    try {
                        return credentials.get().stream();
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new RuntimeException("Error reading credentials", ex);
        }
        return new Download(concurrentDownloadCount, cacheDir, directCredentials);
    }

    List<URL> getLocalURLs(List<URL> urls) throws MalformedURLException {

        ThreadFactory threadFactory = new ThreadFactory() {
            AtomicInteger counter = new AtomicInteger(1);
            ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
            public Thread newThread(Runnable r) {
                String name = "coursier-bootstrap-downloader-" + counter.getAndIncrement();
                Thread t = defaultThreadFactory.newThread(r);
                t.setName(name);
                t.setDaemon(true);
                return t;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(concurrentDownloadCount, threadFactory);

        try {
            return getLocalURLs(urls, pool);
        } finally {
            pool.shutdown();
        }
    }

    private void doDownload(URL url, File tmpDest, File dest) throws IOException {
        URLConnection conn = url.openConnection();
        if (conn instanceof HttpURLConnection) {
            final Optional<String> userInfoOpt = Optional.ofNullable(url.getUserInfo());
            final Optional<String> userInfoUserOpt = userInfoOpt.map(userInfo -> userInfo.split(":", 2)[0]);
            final Optional<DirectCredentials> directCredentialsOpt = directCredentials.stream()
                .filter(DirectCredentials::isMatchHost)
                .filter(credentials -> credentials.getUsernameOpt().isPresent() && (!userInfoUserOpt.isPresent() || credentials.getUsernameOpt().get().equals(userInfoUserOpt.get())))
                .filter(credentials -> credentials.getPasswordOpt().isPresent())
                .filter(credentials -> ("http".equals(url.getProtocol()) && !credentials.isHttpsOnly()) || "https".equals(url.getProtocol()))
                .filter(credentials -> credentials.getHost().equals(url.getHost()))
                .findFirst();
            final Optional<String> userOpt = userInfoUserOpt.map(Optional::of).orElse(directCredentialsOpt.flatMap(credentials -> credentials.getUsernameOpt())); // Java 9: .or(() -> directCredentialsOpt.flatMap(credentials -> credentials.getUsername()));
            final Optional<String> basicAuthOpt = userOpt.flatMap(user ->
                userInfoOpt
                    .map(userInfo -> userInfo.split(":", 2))
                    .flatMap(userInfo -> (userInfo.length == 2) ? Optional.of(userInfo[1]) : Optional.empty())
                    .map(Optional::of).orElse(directCredentialsOpt.flatMap(credentials -> credentials.getPasswordOpt().map(password -> password.getValue()))) // Java 9: .or(() -> directCredentialsOpt.flatMap(credentials -> credentials.getPasswordOpt().map(password -> password.getValue())))
                    .map(password -> Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)))
            );
            basicAuthOpt.ifPresent(basicAuth -> ((HttpURLConnection)conn).setRequestProperty("Authorization", "Basic " + basicAuth));
        }
        long lastModified = conn.getLastModified();
        int size = conn.getContentLength();
        InputStream s = conn.getInputStream();
        byte[] b = Util.readFullySync(s);
        // Seems java.net.HttpURLConnection doesn't always throw if the connection gets
        // abruptly closed during transfer, hence this extra check.
        if (size >= 0 && b.length != size) {
            throw new RuntimeException(
                    "Error downloading " + url + " " +
                            "(expected " + size + " B, got " + b.length + " B), " +
                            "try again");
        }
        tmpDest.deleteOnExit();
        Util.writeBytesToFile(tmpDest, b);
        tmpDest.setLastModified(lastModified);
        Files.move(tmpDest.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }

    private List<URL> getLocalURLs(List<URL> urls, ExecutorService pool) throws MalformedURLException {

        CompletionService<URL> completionService =
                new ExecutorCompletionService<>(pool);

        List<URL> localURLs = new ArrayList<>();
        List<URL> missingURLs = new ArrayList<>();

        for (URL url : urls) {

            String protocol = url.getProtocol();

            if (protocol.equals("file") || protocol.equals("jar")) {
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
                boolean retry = true;
                boolean warnedConcurrentDownload = false;

                final File tmpDest = CachePath.temporaryFile(dest);
                final File lockFile = CachePath.lockFile(tmpDest);

                while (!dest.exists() && retry) {
                    retry = false;

                    try (FileOutputStream out = CachePath.withStructureLock(cacheDir, () -> {
                        coursier.paths.Util.createDirectories(tmpDest.toPath().getParent());
                        coursier.paths.Util.createDirectories(lockFile.toPath().getParent());
                        coursier.paths.Util.createDirectories(dest.toPath().getParent());

                        return new FileOutputStream(lockFile);
                    })) {

                        try (FileLock lock = out.getChannel().tryLock()) {
                            if (lock == null) {
                                if (!warnedConcurrentDownload) {
                                    System.err.println("Waiting for ongoing concurrent download for " + url);
                                    warnedConcurrentDownload = true;
                                }
                                Thread.sleep(20L);
                                retry = true;
                            } else
                                try {
                                    doDownload(url, tmpDest, dest);
                                }
                                finally {
                                    lockFile.delete();
                                }
                        }
                        catch (OverlappingFileLockException e) {
                            if (!warnedConcurrentDownload) {
                                System.err.println("Waiting for ongoing concurrent download for " + url);
                                warnedConcurrentDownload = true;
                            }
                            Thread.sleep(20L);
                            retry = true;
                        }
                        catch (IOException e) {
                            if (e.getMessage().contains("Resource deadlock avoided")) {
                                Thread.sleep(200L);
                                retry = true;
                            } else
                                throw e;
                        }
                    } catch (Exception e) {
                        System.err.println("Error while downloading " + url + ": " + e.getMessage() + ", ignoring it");
                        throw e;
                    }
                }

                return dest.toURI().toURL();
            });
        }

        boolean useAnsiOutput = coursier.paths.Util.useAnsiOutput();
        String clearLine;
        String up;
        if (useAnsiOutput) {
            clearLine = "\033[2K";
            up = "\033[1A";
        } else {
            clearLine = "";
            up = "";
        }

        try {
            while (localURLs.size() < urls.size()) {
                Future<URL> future = completionService.take();
                try {
                    URL url = future.get();
                    localURLs.add(url);
                    int nowMissing = urls.size() - localURLs.size();
                    System.err.print(clearLine + "Downloaded " + (missingURLs.size() - nowMissing) + " missing file(s) / " + missingURLs.size() + "\n" + up);
                } catch (ExecutionException ex) {
                    // Error message already printed from the Callable above
                    System.exit(255);
                }
            }
        } catch (InterruptedException ex) {
            // ???
            System.err.println("Interrupted");
            System.exit(1);
        }

        if (!missingURLs.isEmpty()) {
            System.err.print(clearLine);
        }

        return localURLs;
    }

}
