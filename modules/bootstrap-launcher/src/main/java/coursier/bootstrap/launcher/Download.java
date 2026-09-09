package coursier.bootstrap.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
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
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

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

    /** Downloads `url`, connecting to `address` if it is non-null rather than to the address the
     * host name resolves to.
     */
    private void doDownloadFrom(URL url, File tmpDest, File dest, InetAddress address, int connectTimeout) throws IOException {
        URLConnection conn = openConnection(url, address, connectTimeout);
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
        checkFaithful(url, conn, address);
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

    /** Downloads `url`, and tries the other addresses of its host if it failed to connect.
     *
     * `HttpURLConnection` only ever connects to the first address a host resolves to, so a host
     * whose first address is unreachable - a load-balanced mirror with a node down, an AAAA record
     * on a machine with no IPv6 route - fails for good. See `coursier.cache.AddressFallback`, which
     * does the same for the downloads coursier itself runs, for the details.
     */
    private void doDownload(URL url, File tmpDest, File dest) throws IOException {

        IOException initialEx;
        try {
            doDownloadFrom(url, tmpDest, dest, null, 0);
            return;
        } catch (IOException e) {
            if (!isConnectionFailure(e)) throw e;
            initialEx = e;
        }

        InetAddress[] addresses = otherAddresses(url);
        if (addresses == null) throw initialEx;

        int connectTimeout = perIpConnectTimeout();
        for (int i = 0; i < addresses.length; i++) {
            // the address that just failed is the first one, and goes last
            InetAddress address = addresses[(i + 1) % addresses.length];
            try {
                doDownloadFrom(url, tmpDest, dest, address, connectTimeout);
                return;
            } catch (IOException e) {
                initialEx.addSuppressed(e);
            }
        }

        throw initialEx;
    }

    /** Fails the attempt if the answer did not come from the server it was meant for
     *
     * An http request pinned to an address goes out as if to a proxy, for the whole exchange: a
     * redirection to another host or port would have been fetched from the pinned address, which is
     * not that host's server. A 400 goes too: that is what a server refusing the absolute request
     * URI a proxy sends - which RFC 7230 requires it to accept - answers. The https attempts are
     * pinned per host, so what comes back is sound whichever way it redirects.
     */
    static void checkFaithful(URL url, URLConnection conn, InetAddress address) throws IOException {
        if (address == null || "https".equals(url.getProtocol()) || !(conn instanceof HttpURLConnection))
            return;
        URL answered = conn.getURL();
        if (!url.getHost().equalsIgnoreCase(answered.getHost())
                || url.getPort() != answered.getPort()
                || ((HttpURLConnection) conn).getResponseCode() == 400)
            throw new IOException(
                    "Cannot download " + url + " from " + address.getHostAddress());
    }

    /** Whether `e` is the kind of failure another address may not run into */
    static boolean isConnectionFailure(IOException e) {
        return (e instanceof SocketException) || (e instanceof SocketTimeoutException);
    }

    /** The addresses of the host of `url`, or null when there is nothing worth trying */
    static InetAddress[] otherAddresses(URL url) {
        String retry = env("COURSIER_RETRY_RESOLVED_IPS", "coursier.retry-resolved-ips");
        if (retry != null && (retry.equalsIgnoreCase("false") || retry.equals("0"))) return null;

        String protocol = url.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) return null;

        String host = url.getHost();
        if (host == null || host.isEmpty()) return null;

        // a proxied connection fails on the proxy's address, which has nothing to do with the
        // addresses the host resolves to
        try {
            ProxySelector selector = ProxySelector.getDefault();
            if (selector != null)
                for (Proxy proxy : selector.select(url.toURI()))
                    if (proxy.type() != Proxy.Type.DIRECT) return null;
        } catch (URISyntaxException | RuntimeException e) {
            // no proxy to be seen
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (IOException e) {
            return null;
        }
        // a literal address resolves to itself, and lands here as a single-element array
        return (addresses.length > 1) ? addresses : null;
    }

    static int perIpConnectTimeout() {
        String value = env("COURSIER_PER_IP_CONNECT_TIMEOUT", "coursier.per-ip-connect-timeout");
        if (value != null) {
            String digits = value.endsWith("ms") ? value.substring(0, value.length() - 2)
                    : value.endsWith("s") ? value.substring(0, value.length() - 1)
                    : value;
            try {
                int amount = Integer.parseInt(digits.trim());
                if (amount >= 0) return value.endsWith("ms") ? amount : amount * 1000;
            } catch (NumberFormatException e) {
                // stick to the default
            }
        }
        return 3000;
    }

    /** The environment variable if it is set, else the Java property, like `coursier.util.EnvEntry` */
    private static String env(String envName, String propName) {
        String value = System.getenv(envName);
        return (value != null) ? value : System.getProperty(propName);
    }

    /** Opens a connection to `url`, going to `address` when it is non-null
     *
     * The URL is left alone, so that the request keeps its Host header, its SNI and the
     * certificate it is checked against: only the connection is redirected.
     */
    static URLConnection openConnection(URL url, InetAddress address, int connectTimeout) throws IOException {
        URLConnection conn;
        if (address == null)
            conn = url.openConnection();
        else if ("https".equals(url.getProtocol())) {
            conn = url.openConnection();
            if (!(conn instanceof HttpsURLConnection))
                throw new IOException("Cannot connect to " + url + " via " + address.getHostAddress());
            HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
            httpsConn.setSSLSocketFactory(
                    new PinnedAddressSslSocketFactory(httpsConn.getSSLSocketFactory(), url.getHost(), address));
        }
        else {
            // an HTTP proxy is handed the URL as it stands, so the request keeps its Host header,
            // and only its destination changes
            int port = (url.getPort() == -1) ? url.getDefaultPort() : url.getPort();
            conn = url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(address, port)));
        }
        if (connectTimeout > 0) conn.setConnectTimeout(connectTimeout);
        return conn;
    }

    /** Creates sockets that connect to `address` rather than to whatever `host` resolves to
     *
     * `HttpsClient` asks the SSL socket factory for an unconnected socket, connects it itself, then
     * hands it back to the factory to be wrapped with SSL, passing the host name from the URL. This
     * socket lands in between, and leaves the SSL layer to the factory it wraps, none the wiser.
     */
    static final class PinnedAddressSslSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory underlying;
        private final String host;
        private final InetAddress address;

        PinnedAddressSslSocketFactory(SSLSocketFactory underlying, String host, InetAddress address) {
            this.underlying = underlying;
            this.host = host;
            this.address = address;
        }

        @Override
        public Socket createSocket() throws IOException {
            return new Socket() {
                @Override
                public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    if (endpoint instanceof InetSocketAddress) {
                        InetSocketAddress inet = (InetSocketAddress) endpoint;
                        // a redirection elsewhere gets to connect where it should
                        if (host.equalsIgnoreCase(inet.getHostString())) {
                            super.connect(new InetSocketAddress(address, inet.getPort()), timeout);
                            return;
                        }
                    }
                    super.connect(endpoint, timeout);
                }
            };
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return underlying.createSocket(s, host, port, autoClose);
        }
        @Override
        public String[] getDefaultCipherSuites() {
            return underlying.getDefaultCipherSuites();
        }
        @Override
        public String[] getSupportedCipherSuites() {
            return underlying.getSupportedCipherSuites();
        }
        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return underlying.createSocket(host, port);
        }
        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return underlying.createSocket(host, port, localHost, localPort);
        }
        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return underlying.createSocket(host, port);
        }
        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return underlying.createSocket(address, port, localAddress, localPort);
        }
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
