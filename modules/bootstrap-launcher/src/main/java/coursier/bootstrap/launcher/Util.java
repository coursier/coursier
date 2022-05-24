package coursier.bootstrap.launcher;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

class Util {

    static byte[] readFullySync(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[16384];

        int nRead = is.read(data, 0, data.length);
        while (nRead != -1) {
            buffer.write(data, 0, nRead);
            nRead = is.read(data, 0, data.length);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    static String[] readStringSequence(String resource, ClassLoader loader) throws IOException {
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            return new String[] {};
        byte[] rawContent = readFullySync(is);
        String content = new String(rawContent, StandardCharsets.UTF_8);
        if (content.length() == 0)
            return new String[] {};
        return content.split("\n");
    }

    static String readString(String resource, ClassLoader loader) throws IOException {
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            return null;
        byte[] rawContent = readFullySync(is);
        return new String(rawContent, StandardCharsets.UTF_8);
    }

    // http://stackoverflow.com/questions/872272/how-to-reference-another-property-in-java-util-properties/27724276#27724276
    static Map<String,String> loadPropertiesMap(
        Properties systemProperties,
        InputStream s) throws IOException {
        final Map<String, String> ordered = new LinkedHashMap<>();
        //Hack to use properties class to parse but our map for preserved order
        Properties bp = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                ordered.put((String)key, (String)value);
                return super.put(key, value);
            }
        };
        bp.load(s);

        return coursier.paths.Util.expandProperties(systemProperties, ordered);
    }

    static String mainJarPath() throws URISyntaxException {
        ProtectionDomain protectionDomain = Bootstrap.class.getProtectionDomain();
        if (protectionDomain != null) {
            CodeSource source = protectionDomain.getCodeSource();
            if (source != null) {
                URL location = source.getLocation();
                if (location != null && location.getProtocol().equals("file")) {
                    return new File(location.toURI()).getPath();
                }
            }
        }

        return "";
    }

    // from http://www.java2s.com/Code/Java/File-Input-Output/Readfiletobytearrayandsavebytearraytofile.htm
    static void writeBytesToFile(File file, byte[] bytes) throws IOException {
        BufferedOutputStream bos = null;

        try {
            FileOutputStream fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bytes);
        } finally {
            if (bos != null) {
                try  {
                    // flush and close the BufferedOutputStream
                    bos.flush();
                    bos.close();
                } catch (Exception ignored) {}
            }
        }
    }

}
