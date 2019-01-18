package coursier.bootstrap.launcher;

public class Launcher {

    public static void main(String[] args) throws Throwable {

        ClassLoaders classLoaders = new ClassLoaders();

        Bootstrap.main(args, classLoaders);
    }

}
