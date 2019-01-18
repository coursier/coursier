package coursier.bootstrap.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

class ClassLoaders {

    ClassLoaders() {
    }

    final static String resourceDir = "coursier/bootstrap/launcher/";
    final static String defaultURLResource = resourceDir + "bootstrap-jar-urls";

    List<URL> getURLs(String[] rawURLs) {

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

        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder("Error:");
            for (String error: errors) {
                builder.append("\n  ");
                builder.append(error);
            }
            System.err.println(builder.toString());
            System.exit(1);
        }

        return urls;
    }

    ClassLoader readBaseLoaders(ClassLoader baseLoader) throws IOException {

        ClassLoader parentLoader = baseLoader;
        int i = 1;
        while (true) {
            String[] strUrls = Util.readStringSequence(resourceDir + "bootstrap-jar-urls-" + i, baseLoader);

            if (strUrls.length == 0)
                break;

            List<URL> urls = getURLs(strUrls);
            List<URL> localURLs = Download.getLocalURLs(urls);

            parentLoader = new URLClassLoader(localURLs.toArray(new URL[0]), parentLoader);

            i = i + 1;
        }

        return parentLoader;
    }

    ClassLoader createClassLoader(ClassLoader contextLoader) throws IOException {

        String[] strUrls = Util.readStringSequence(defaultURLResource, contextLoader);
        List<URL> urls = getURLs(strUrls);
        List<URL> localURLs = Download.getLocalURLs(urls);

        ClassLoader parentClassLoader = readBaseLoaders(contextLoader);

        return new URLClassLoader(localURLs.toArray(new URL[0]), parentClassLoader);
    }

}
