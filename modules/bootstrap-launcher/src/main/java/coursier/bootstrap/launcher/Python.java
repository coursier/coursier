package coursier.bootstrap.launcher;

import java.util.List;
import java.util.Map;

import coursier.paths.Jep;

class Python {

    private static void setPythonProperties() throws Exception {
        List<Map.Entry<String, String>> properties = Jep.pythonProperties();
        for (Map.Entry<String, String> entry : properties) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.setProperty(key, value);
        }
    }

    static void maybeSetPythonProperties(ClassLoader contextLoader) throws Exception {
        // only checking if that file exists for now
        String resource = ClassLoaders.resourceDir + "set-python-properties";
        boolean doSetProps = contextLoader.getResource(resource) != null;
        if (doSetProps) {
            setPythonProperties();
        }
    }

}
