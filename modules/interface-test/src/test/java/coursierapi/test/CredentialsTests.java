package coursierapi.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import coursierapi.Cache;
import coursierapi.Credentials;
import coursierapi.Dependency;
import coursierapi.Fetch;
import coursierapi.MavenRepository;
import coursierapi.error.CoursierError;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cache passed to `coursierapi.Fetch` used to be honoured when downloading POMs, but not
 * when downloading artifacts, which then used the default cache - without any of the credentials
 * that were meant to be used, see https://github.com/coursier/interface/issues/460
 */
public class CredentialsTests {

    private static final String USER = "alex";
    private static final String PASSWORD = "1234";

    private static final String POM = String.join("\n",
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<project xmlns='http://maven.apache.org/POM/4.0.0'>",
            "  <modelVersion>4.0.0</modelVersion>",
            "  <groupId>org.example</groupId>",
            "  <artifactId>foo</artifactId>",
            "  <version>1.0</version>",
            "  <packaging>jar</packaging>",
            "</project>",
            "");

    /** A Maven repository that hands out its content only to requests with the right credentials */
    private static final class AuthenticatedRepository implements AutoCloseable, HttpHandler {

        private final Map<String, byte[]> content = new HashMap<>();
        private final String expectedAuthorization;
        private final HttpServer server;

        AuthenticatedRepository() throws IOException {
            content.put("/org/example/foo/1.0/foo-1.0.pom", POM.getBytes(StandardCharsets.UTF_8));
            content.put("/org/example/foo/1.0/foo-1.0.jar", "not a real JAR".getBytes(StandardCharsets.UTF_8));

            String userPassword = USER + ":" + PASSWORD;
            expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString(userPassword.getBytes(StandardCharsets.UTF_8));

            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this);
            server.start();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                byte[] response = content.get(exchange.getRequestURI().getPath());
                if (response == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                if (!expectedAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"test-realm\"");
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } finally {
                exchange.close();
            }
        }

        String base() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/";
        }

        String host() {
            return server.getAddress().getHostString();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static void checkFetches(Cache cache, AuthenticatedRepository repository, Path cacheLocation) {

        Fetch fetch = Fetch.create()
                .withRepositories(MavenRepository.of(repository.base()))
                .addDependencies(Dependency.of("org.example", "foo", "1.0"))
                .withCache(cache);

        List<File> files;
        try {
            files = fetch.fetch();
        } catch (CoursierError e) {
            throw new RuntimeException(e);
        }

        assertEquals(1, files.size());
        File jar = files.get(0);
        assertEquals("foo-1.0.jar", jar.getName());
        // the artifacts used to be downloaded in the default cache, rather than the one we passed
        assertTrue(
                "Expected " + jar + " to live under " + cacheLocation,
                jar.toPath().toAbsolutePath().startsWith(cacheLocation.toAbsolutePath())
        );
    }

    @Test
    public void inlineCredentials() throws IOException {
        Path cacheLocation = Files.createTempDirectory("coursier-interface-test-cache-");
        try (AuthenticatedRepository repository = new AuthenticatedRepository()) {
            Cache cache = Cache.create()
                    .withLocation(cacheLocation.toFile())
                    .addCredentials(Credentials.of(repository.host(), USER, PASSWORD));
            checkFetches(cache, repository, cacheLocation);
        }
    }

    @Test
    public void fileCredentials() throws IOException {
        Path cacheLocation = Files.createTempDirectory("coursier-interface-test-cache-");
        Path credentialsFile = Files.createTempFile("coursier-interface-test-credentials-", ".properties");
        try (AuthenticatedRepository repository = new AuthenticatedRepository()) {
            Files.write(credentialsFile, String.join("\n",
                    "test.host=" + repository.host(),
                    "test.username=" + USER,
                    "test.password=" + PASSWORD,
                    "").getBytes(StandardCharsets.UTF_8));
            Cache cache = Cache.create()
                    .withLocation(cacheLocation.toFile())
                    .addFileCredentials(credentialsFile.toFile());
            checkFetches(cache, repository, cacheLocation);
        } finally {
            Files.deleteIfExists(credentialsFile);
        }
    }

}
