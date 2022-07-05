package coursier.bootstrap.launcher;

import java.net.URL;
import java.net.URLClassLoader;

// Mostly deprecated.
// Adds a getIsolationTargets method to a URLClassLoader, that users can call by reflection
// to find a specific classloader.
// As a substitute, users should call classOf[…].getClassLoader on a class they know is in a shared
// classloader, to find specific classloaders.
public class SharedClassLoader extends URLClassLoader {

    private final String[] isolationTargets;

    public SharedClassLoader(
            URL[] urls,
            ClassLoader parent,
            String[] isolationTargets
    ) {
        super(urls, parent);
        this.isolationTargets = isolationTargets;
    }

    /**
     * Applications wanting to access an isolated `ClassLoader` should inspect the hierarchy of
     * loaders, and look into each of them for this method, by reflection. Then they should
     * call it (still by reflection), and look for an agreed in advance target in it. If it is found,
     * then the corresponding `ClassLoader` is the one with isolated dependencies.
     */
    public String[] getIsolationTargets() {
        return isolationTargets;
    }

}