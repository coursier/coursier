package coursier.bootstrap.launcher;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.Locale;
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
            // not following symbolic links, file being in the cache, possibly writable by others
            coursier.paths.Util.deleteIfSymbolicLink(file.toPath());
            OutputStream fos = Files.newOutputStream(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
            );
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

    static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            // every Java platform is required to support SHA-256
            throw new RuntimeException(ex);
        }
    }

    static String toHex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte b : digest)
            builder.append(String.format("%02x", b & 0xff));
        return builder.toString();
    }

    static String sha256(byte[] content) {
        return toHex(sha256Digest().digest(content));
    }

    static String sha256(File file) throws IOException {
        MessageDigest md = sha256Digest();
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[16384];
            int read = is.read(buf);
            while (read >= 0) {
                md.update(buf, 0, read);
                read = is.read(buf);
            }
        }
        return toHex(md.digest());
    }

    /**
     * Checks that the actual SHA-256 of a class path JAR matches the one recorded in the launcher.
     *
     * @param expected hex-encoded SHA-256 recorded in the launcher, null or empty if unknown
     */
    static void checkSha256(String expected, String actual, String url, String location) throws IOException {
        if (expected == null || expected.isEmpty())
            return;
        if (!expected.trim().toLowerCase(Locale.ROOT).equals(actual))
            throw new IOException(
                "SHA-256 mismatch for " + url + " (" + location + "): " +
                    "expected " + expected.trim() + ", got " + actual + ". " +
                    "The JAR differs from the one this launcher was generated with, refusing to use it.");
    }

}
