package coursier.bootstrap.launcher;

import coursier.bootstrap.launcher.jar.JarEntry;
import coursier.bootstrap.launcher.jar.JarFile;
import coursier.paths.Mirror.MirrorPropertiesException;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

class ResourcesClassLoaders extends ClassLoaders {

    private final JarFile sourceJarFile;

    ResourcesClassLoaders(JarFile sourceJarFile, Download download) throws MirrorPropertiesException, IOException  {
        super(download);
        this.sourceJarFile = sourceJarFile;
    }

    private final static String jarDir = ClassLoaders.resourceDir + "jars/";
    private final static String defaultJarResource = ClassLoaders.resourceDir + "bootstrap-jar-resources";

    private List<URL> getResourceURLs(String[] resources) throws IOException {

        List<String> errors = new ArrayList<>();
        List<URL> urls = new ArrayList<>();

        for (String resource : resources) {
            JarEntry entry = sourceJarFile.getJarEntry(jarDir + resource);
            if (entry == null) {
                String message = "Resource " + resource + " not found";
                errors.add(message);
            } else {
                // Adds '!/' at the end of the URLs compared to entry.getUrl().
                // It seems to sometimes work without it, but usually fails most of the time…
                urls.add(sourceJarFile.getNestedJarFile(entry).getUrl());
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
            String[] strUrls = Util.readStringSequence(ClassLoaders.resourceDir + "bootstrap-jar-urls-" + i, baseLoader);
            String[] resources = Util.readStringSequence(ClassLoaders.resourceDir + "bootstrap-jar-resources-" + i, baseLoader);

            if (strUrls.length == 0 && resources.length == 0)
                break;

            List<URL> urls = getURLs(strUrls);
            List<URL> resourceURLs = getResourceURLs(resources);
            urls.addAll(resourceURLs);
            List<URL> localURLs = download.getLocalURLs(urls);

            parentLoader = new URLClassLoader(localURLs.toArray(new URL[0]), parentLoader);

            i = i + 1;
        }

        return parentLoader;
    }

    ClassLoader createClassLoader(ClassLoader contextLoader) throws IOException {

        String[] strUrls = Util.readStringSequence(ClassLoaders.defaultURLResource, contextLoader);
        String[] resources = Util.readStringSequence(defaultJarResource, contextLoader);
        List<URL> urls = getURLs(strUrls);
        List<URL> resourceURLs = getResourceURLs(resources);
        urls.addAll(resourceURLs);
        List<URL> localURLs = download.getLocalURLs(urls);

        ClassLoader parentClassLoader = readBaseLoaders(contextLoader);

        return new URLClassLoader(localURLs.toArray(new URL[0]), parentClassLoader);
    }

}
