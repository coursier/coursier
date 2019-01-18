package coursier.bootstrap.launcher;

import coursier.bootstrap.launcher.jar.JarFile;

import java.io.File;
import java.net.URL;

public class ResourcesLauncher {

    public static void main(String[] args) throws Throwable {

        JarFile.registerUrlProtocolHandler();

        URL source = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
        File sourceFile = new File(source.toURI());
        JarFile sourceJarFile = new JarFile(sourceFile);

        ClassLoaders classLoaders = new ResourcesClassLoaders(sourceJarFile);

        Bootstrap.main(args, classLoaders);
    }

}
