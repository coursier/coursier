package coursier.bootstrap.launcher;

import coursier.bootstrap.launcher.jar.JarFile;
import coursier.bootstrap.launcher.jniutils.BootstrapNativeApi;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;

public class ResourcesLauncher {

    private static void disableJarChecking() {
        if (System.getProperty("sun.misc.URLClassPath.disableJarChecking") == null) {

            // I would have simply wanted to do a
            //   System.setProperty("sun.misc.URLClassPath.disableJarChecking", "true")
            // here.
            // The spring boot loader manages to set that property, see
            //   https://github.com/spring-projects/spring-boot/issues/1117#issuecomment-46372242,
            //   https://github.com/spring-projects/spring-boot/blob/b339c92871ee08d9f36bb0f1b5224311f6eef0bd/spring-boot-project/spring-boot-tools/spring-boot-loader-tools/src/main/resources/org/springframework/boot/loader/tools/launch.script#L146
            // Spring sets it via bash. This would work for us too, but only on Linux / OS X, when users directly call
            // the bootstrap (not when they call it via  java -jar bootstrap).

            // Calling System.setProperty doesn't work, as the field that reads this property,
            // sun.misc.URLClassPath.DISABLE_JAR_CHECKING, is statically initialized (and private final too…). We would
            // set the property too late here.

            // So we have to go though hoops and loops to achieve that anyway…

            String verboseProp = System.getProperty("coursier.bootstrap.launcher.disableJarChecking.verbose");
            boolean verbose = "true".equals(verboseProp) || "".equals(verboseProp);

            try {
                Field field = Thread.currentThread()
                        .getContextClassLoader()
                        .loadClass("sun.misc.URLClassPath")
                        .getDeclaredField("DISABLE_JAR_CHECKING");

                // https://stackoverflow.com/a/3301720/3714539
                field.setAccessible(true);
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

                field.setBoolean(null, true);
            } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException ex) {
                if (verbose) {
                    System.err.println("Warning: caught " + ex + " while trying to force " +
                            "sun.misc.URLClassPath.disableJarChecking");
                    ex.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws Throwable {

        disableJarChecking();

        JarFile.registerUrlProtocolHandler();

        URL source = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
        File sourceFile = new File(source.toURI());
        JarFile sourceJarFile = new JarFile(sourceFile);

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("windows");

        if (isWindows)
            coursier.paths.Util.useJni(BootstrapNativeApi::setup);

        Download download = Download.getDefault();

        ClassLoaders classLoaders = new ResourcesClassLoaders(sourceJarFile, download);

        Bootstrap.main(args, classLoaders);
    }

}
