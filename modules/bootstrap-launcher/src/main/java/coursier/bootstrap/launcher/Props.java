package coursier.bootstrap.launcher;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

class Props {

    static void setMainProperties(String[] args) throws URISyntaxException {
        String mainJarPath = Util.mainJarPath();
        System.setProperty("coursier.mainJar", mainJarPath);

        for (int i = 0; i < args.length; i++) {
            System.setProperty("coursier.main.arg-" + i, args[i]);
        }
    }

    static void setExtraProperties(ClassLoader loader) throws IOException {
        String resource = ClassLoaders.resourceDir + "bootstrap.properties";
        Map<String,String> properties = Util.loadPropertiesMap(
            System.getProperties(),
            loader.getResourceAsStream(resource));
        for (Map.Entry<String, String> ent : properties.entrySet()) {
            System.setProperty(ent.getKey(), ent.getValue());
        }
    }

}
