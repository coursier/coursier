package coursier.bootstrap.launcher;

import coursier.bootstrap.launcher.jar.JarFile;
import coursier.paths.Jep;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

class Python {

    static void exit(String message) {
        System.err.println(message);
        System.exit(255);
    }

    static void closeLoader(ClassLoader loader, ClassLoader until) throws IOException {
        ClassLoader loader0 = loader;
        while (loader0 != until && loader0 != null) {
            if (loader0 instanceof URLClassLoader)
                ((URLClassLoader) loader0).close();
            loader0 = loader0.getParent();
        }
    }

    private static void setPythonProperties(JarFile sourceJarFileOrNull, Download download, ClassLoader contextLoader) throws Throwable {

        final ClassLoaders classLoaders;
        if (sourceJarFileOrNull == null)
          classLoaders = new ClassLoaders(download, "python");
        else
          classLoaders = new ResourcesClassLoaders(sourceJarFileOrNull, download, "python");
        ClassLoader classLoader = classLoaders.createClassLoader(contextLoader);

        String libsClassName = "io.github.alexarchambault.pythonnativelibs.PythonNativeLibs";
        Class<?> libsClass = null;
        Method propsMethod = null;

        try {
            libsClass = classLoader.loadClass(libsClassName);
        } catch (ClassNotFoundException ex) {
            exit("Error: class " + libsClassName + " not found");
        }

        try {
            Class<?>[] params = {};
            assert libsClass != null;
            propsMethod = libsClass.getMethod("scalapyProperties", params);
        }
        catch (NoSuchMethodException ex) {
            exit("Error: scalapyProperties method not found in class " + libsClassName);
        }

        Map<String, String> props = null;

        Thread thread = Thread.currentThread();
        thread.setContextClassLoader(classLoader);
        try {
            Object[] propsArgs = {};
            assert propsMethod != null;
            props = (Map<String, String>) propsMethod.invoke(null, propsArgs);
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

        assert props != null;
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.setProperty(key, value);
        }

        closeLoader(classLoader, contextLoader);
    }

    private static void setPythonJepProperties() throws Exception {
        List<Map.Entry<String, String>> properties = Jep.pythonProperties();
        for (Map.Entry<String, String> entry : properties) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.setProperty(key, value);
        }
    }

    static void maybeSetPythonProperties(JarFile sourceJarFileOrNull, Download download, ClassLoader contextLoader) throws Throwable {
        // only checking if that file exists for now
        String jepResource = ClassLoaders.resourceDir + "set-python-jep-properties";
        boolean doSetJepProps = contextLoader.getResource(jepResource) != null;
        if (doSetJepProps) {
            setPythonJepProperties();
        }

        // only checking if that file exists for now
        String resource = ClassLoaders.resourceDir + "set-python-properties";
        boolean doSetProps = contextLoader.getResource(resource) != null;
        if (doSetProps) {
            setPythonProperties(sourceJarFileOrNull, download, contextLoader);
        }
    }

}
