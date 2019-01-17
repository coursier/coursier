package coursier.bootstrap.launcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Random;

// JARs from JARs can't be used directly, see:
// http://stackoverflow.com/questions/183292/classpath-including-jar-within-a-jar/2326775#2326775
// so we resort to passing an explicit URLStreamHandler to URLs
class BootstrapURLStreamHandlerFactory {
    private final URLStreamHandler handler;
    private final String bootstrapProtocol;

    BootstrapURLStreamHandlerFactory(String basePath, ClassLoader loader) {
        Random rng = new Random();
        this.bootstrapProtocol = "bootstrap" + rng.nextLong();
        handler = new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                String path = url.getPath();
                URL resURL = loader.getResource(basePath + path);
                if (resURL == null)
                    throw new FileNotFoundException("Resource " + basePath + path);
                return resURL.openConnection();
            }
        };
    }

    String getProtocol() {
        return bootstrapProtocol;
    }

    URL createURL(String resource) throws MalformedURLException {
        return new URL(
                bootstrapProtocol,
                null,
                -1,
                resource,
                handler);
    }
}
