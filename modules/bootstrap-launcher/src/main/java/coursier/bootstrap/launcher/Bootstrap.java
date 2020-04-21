package coursier.bootstrap.launcher;

import java.io.File;
import java.io.IOException;
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

    private static void maybeInitWindowsAnsi() throws InterruptedException, IOException {
        if (!System.getProperty("coursier.bootstrap.windows-ansi.disable", "").equalsIgnoreCase("false")) {
            try {
                // noop on Linux / macOS
                io.github.alexarchambault.windowsansi.WindowsAnsiPs.setup();
            } catch (InterruptedException | IOException e) {
                boolean doThrow = Boolean.getBoolean("coursier.bootstrap.windows-ansi.throw-exception");
                if (doThrow || Boolean.getBoolean("coursier.bootstrap.windows-ansi.verbose"))
                    System.err.println("Error setting up Windows terminal for ANSI escape codes: " + e);
                if (doThrow)
                    throw e;
            }
        }
    }

    static void main(
            String[] args,
            ClassLoaders classLoaders) throws Throwable {

        maybeInitWindowsAnsi();

        Thread thread = Thread.currentThread();
        ClassLoader contextLoader = thread.getContextClassLoader();

        ClassLoader classLoader = classLoaders.createClassLoader(contextLoader);

        boolean isSimpleLoader = classLoader.getParent().equals(contextLoader) && (classLoader instanceof URLClassLoader);
        String previousJavaClassPath = null;

        if (isSimpleLoader) {
            boolean urlsInJavaClassPath = Boolean.getBoolean("coursier.bootstrap.urls-in-jcp");
            URL[] urls = ((URLClassLoader) classLoader).getURLs();
            StringBuilder b = new StringBuilder();
            previousJavaClassPath = System.getProperty("java.class.path");
            if (previousJavaClassPath != null) {
                b.append(previousJavaClassPath);
            }
            for (URL url : urls) {
                if (url.getProtocol().equals("file")) {
                    if (b.length() != 0)
                        b.append(File.pathSeparatorChar);
                    b.append(Paths.get(url.toURI()).toString());
                } else if (urlsInJavaClassPath) {
                    if (b.length() != 0)
                        b.append(File.pathSeparatorChar);
                    b.append(url.toExternalForm());
                }
            }
            System.setProperty("java.class.path", b.toString());
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
