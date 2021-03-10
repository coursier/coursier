package coursier.bootstrap.launcher;

public class Launcher {

    public static void main(String[] args) throws Throwable {

        Download download = Download.getDefault();
        ClassLoaders classLoaders = new ClassLoaders(download);

        Bootstrap.main(args, classLoaders);
    }

}
