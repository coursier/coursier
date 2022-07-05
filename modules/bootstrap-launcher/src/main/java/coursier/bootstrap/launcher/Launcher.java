package coursier.bootstrap.launcher;

import coursier.bootstrap.launcher.jniutils.BootstrapNativeApi;

public class Launcher {

    public static void main(String[] args) throws Throwable {

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("windows");

        if (isWindows)
            coursier.paths.Util.useJni(BootstrapNativeApi::setup);

        Download download = Download.getDefault();
        ClassLoaders classLoaders = new ClassLoaders(download);

        Bootstrap.main(args, classLoaders);
    }

}
