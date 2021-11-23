package coursier.bootstrap.launcher;

public class Launcher {

    public static void main(String[] args) throws Throwable {

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("windows");

        if (isWindows)
            coursier.paths.Util.useJni(() -> {
                coursier.bootstrap.launcher.jniutils.BootstrapNativeApi.setup();
            });

        Download download = Download.getDefault();
        ClassLoaders classLoaders = new ClassLoaders(download);

        Bootstrap.main(args, classLoaders);
    }

}
