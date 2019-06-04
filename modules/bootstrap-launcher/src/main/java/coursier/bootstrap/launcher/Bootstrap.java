package coursier.bootstrap.launcher;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

public class Bootstrap {

    private static void exit(String message) {
        System.err.println(message);
        System.exit(255);
    }

    static void main(
            String[] args,
            ClassLoaders classLoaders) throws Throwable {

        Thread thread = Thread.currentThread();
        ClassLoader contextLoader = thread.getContextClassLoader();

        ClassLoader classLoader = classLoaders.createClassLoader(contextLoader);

        boolean isSimpleLoader = classLoader.getParent().equals(contextLoader) && (classLoader instanceof URLClassLoader);
        String previousJavaClassPath = null;

        if (isSimpleLoader) {
            URL[] urls = ((URLClassLoader) classLoader).getURLs();
            StringBuilder b = new StringBuilder();
            for (URL url : urls) {
                if (b.length() != 0)
                    b.append(File.pathSeparatorChar);
                if (url.getProtocol().equals("file")) {
                    b.append(Paths.get(url.toURI()).toString());
                } else {
                    b.append(url.toExternalForm());
                }
            }
            previousJavaClassPath = System.setProperty("java.class.path", b.toString());
        }

        // Called after having set java.class.path, for property expansion
        Props.setMainProperties(args);
        Props.setExtraProperties(contextLoader);

        String mainClass0 = System.getProperty("bootstrap.mainClass");

        Class<?> mainClass = null;
        Method mainMethod = null;

        try {
            mainClass = classLoader.loadClass(mainClass0);
        } catch (ClassNotFoundException ex) {
            exit("Error: class " + mainClass0 + " not found");
        }

        try {
            Class[] params = { String[].class };
            mainMethod = mainClass.getMethod("main", params);
        }
        catch (NoSuchMethodException ex) {
            exit("Error: main method not found in class " + mainClass0);
        }

        thread.setContextClassLoader(classLoader);
        try {
            Object[] mainArgs = { args };
            mainMethod.invoke(null, mainArgs);
        }
        catch (IllegalAccessException ex) {
            exit(ex.getMessage());
        }
        catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
        finally {
            thread.setContextClassLoader(contextLoader);
            if (isSimpleLoader) {
                if (previousJavaClassPath == null) {
                    System.clearProperty("java.class.path");
                } else {
                    System.setProperty("java.class.path", previousJavaClassPath);
                }
            }
        }
    }

}
