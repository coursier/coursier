package coursier.bootstrap.launcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Random;

// JARs from JARs can't be used directly, see:
// http://stackoverflow.com/questions/183292/classpath-including-jar-within-a-jar/2326775#2326775
// Loading them via a custom protocol, inspired by:
// http://stackoverflow.com/questions/26363573/registering-and-using-a-custom-java-net-url-protocol/26409796#26409796
public class BootstrapURLStreamHandlerFactory implements URLStreamHandlerFactory {
    private final String bootstrapProtocol;
    private final String basePath;
    private final ClassLoader loader;
    private boolean registered = false;

    public BootstrapURLStreamHandlerFactory(String bootstrapProtocol, String basePath, ClassLoader loader) {
        this.bootstrapProtocol = bootstrapProtocol;
        this.basePath = basePath;
        this.loader = loader;
    }

    public BootstrapURLStreamHandlerFactory(String basePath, ClassLoader loader) {
        Random rng = new Random();
        this.bootstrapProtocol = "bootstrap" + rng.nextLong();
        this.basePath = basePath;
        this.loader = loader;
    }

    public URLStreamHandler createURLStreamHandler(String protocol) {
        return bootstrapProtocol.equals(protocol) ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                String path = url.getPath();
                URL resURL = loader.getResource(basePath + path);
                if (resURL == null)
                    throw new FileNotFoundException("Resource " + basePath + path);
                return resURL.openConnection();
            }
        } : null;
    }

    public String getProtocol() {
        registerIfNeeded();
        return bootstrapProtocol;
    }

    public void register() {
        URL.setURLStreamHandlerFactory(this);
        registered = true;
    }
    public void registerIfNeeded() {
        if (!registered)
          register();
    }
}
