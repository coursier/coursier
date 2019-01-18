package coursier.bootstrap.launcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

        Props.setMainProperties(args);
        Props.setExtraProperties(contextLoader);

        String mainClass0 = System.getProperty("bootstrap.mainClass");

        ClassLoader classLoader = classLoaders.createClassLoader(contextLoader);

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
        }
    }

}
