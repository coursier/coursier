package coursier.bootstrap.launcher;

import coursier.bootstrap.launcher.jar.JarFile;
import coursier.bootstrap.launcher.proxy.SetupProxy;
import coursier.paths.CoursierPaths;
import coursier.paths.Mirror;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public final class Config {

    private static String csPath = null;

    private static boolean isWindows = System.getProperty("os.name")
        .toLowerCase(java.util.Locale.ROOT)
        .contains("windows");

    /**
     * Looks for an executable named {@code name} in the entries of {@code rawPath}.
     *
     * Relative and empty entries are ignored, so that the lookup never depends on the
     * current directory. On Windows, the extensions of {@code rawPathExt} are tried.
     *
     * @return the absolute path of the executable, or null if it can't be found
     */
    static String findInPath(String name, String rawPath, String rawPathExt, boolean windows) {
        String[] exts;
        if (windows) {
            exts = rawPathExt == null ? new String[] {} : rawPathExt.split(File.pathSeparator);
        } else {
            exts = new String[] {""};
        }

        String[] paths = rawPath == null ? new String[] {} : rawPath.split(File.pathSeparator);

        for (String path : paths) {
            if (path.isEmpty())
                continue;
            File p = new File(path);
            if (!p.isAbsolute())
                continue;
            for (String ext : exts) {
                if (windows && ext.isEmpty())
                    continue;
                File candidate = new File(p, name + ext);
                if (candidate.isFile() && candidate.canExecute())
                    return candidate.getAbsolutePath();
            }
        }

        return null;
    }

    private static String csCommand() throws IOException {
        if (csPath == null) {
            csPath = findInPath("cs", System.getenv("PATH"), System.getenv("PATHEXT"), isWindows);
            if (csPath == null)
                throw new IOException("cs command not found in PATH");
        }
        return csPath;
    }

    private static void printVersionMessage() {
        System.err.println("Note that a cs command from coursier >= v2.1.0-M7-34-gf519b50a3 is recommended.");
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int read = is.read(buf);
        while (read >= 0) {
            if (read > 0)
                baos.write(buf,0, read);
            read = is.read(buf);
        }
        return baos.toByteArray();
    }

    static String[] mirrorValues(String input) {
        String[] split0 = input.split("=", 2);
        if (split0.length != 2)
          throw new RuntimeException("Error parsing mirror value '" + input + "'");
        String[] split1 = split0[1].split(";");

        ArrayList<String> res = new ArrayList<>();

        res.add(split0[0].trim());

        for (String s : split1) {
            String s0 = s.trim();
            if (s0.length() > 0) {
                res.add(s0);
            }
        }

        return res.toArray(new String[res.size()]);
    }

    static Mirror parseMirror(String input) {
        if (input.startsWith("tree:")) {
            String input0 = input.substring("tree:".length());
            String[] values = mirrorValues(input0);
            ArrayList<String> from = new ArrayList<>(Arrays.asList(values));
            from.remove(0);
            return Mirror.of(from, values[0], Mirror.Types.TREE);
        } else if (input.startsWith("maven:")) {
            String input0 = input.substring("maven:".length());
            String[] values = mirrorValues(input0);
            ArrayList<String> from = new ArrayList<>(Arrays.asList(values));
            from.remove(0);
            return Mirror.of(from, values[0], Mirror.Types.MAVEN);
        } else {
            String[] values = mirrorValues(input);
            ArrayList<String> from = new ArrayList<>(Arrays.asList(values));
            from.remove(0);
            return Mirror.of(from, values[0], Mirror.Types.MAVEN);
        }
    }

    static Mirror[] mirrors(String configFile) throws IOException, InterruptedException {

        Process proc = new ProcessBuilder(csCommand(), "config", "repositories.mirrors", "--config-file", configFile)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();

        byte[] output = readAllBytes(proc.getInputStream());
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            System.err.println("Warning: failed to read proxy address from " + configFile + ", ignoring it.");
            printVersionMessage();
            return new Mirror[] {};
        }
        String content = new String(output, StandardCharsets.UTF_8).trim();
        String[] rawMirrors = content.split(System.lineSeparator());

        ArrayList<Mirror> mirrors = new ArrayList<>();
        for (String rawMirror : rawMirrors) {
            Mirror m = parseMirror(rawMirror);
            mirrors.add(m);
        }

        return mirrors.toArray(new Mirror[mirrors.size()]);
    }

    private static String proxyAddress(String configFile) throws IOException, InterruptedException {

        Process proc = new ProcessBuilder(csCommand(), "config", "httpProxy.address", "--config-file", configFile)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();

        byte[] output = readAllBytes(proc.getInputStream());
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            System.err.println("Warning: failed to read proxy address from " + configFile + ", ignoring it.");
            printVersionMessage();
            return "";
        }
        return new String(output, StandardCharsets.UTF_8).trim();
    }

    private static String proxyPasswordParam(String configFile, String keyName) throws IOException, InterruptedException {

        Process proc = new ProcessBuilder(csCommand(), "config", "httpProxy." + keyName, "--password", "--config-file", configFile)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();

        byte[] output = readAllBytes(proc.getInputStream());
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            System.err.println("Warning: failed to read proxy " + keyName + " from " + configFile + ", ignoring it.");
            printVersionMessage();
            return "";
        }
        return new String(output, StandardCharsets.UTF_8).trim();
    }

    private static String proxyUser(String configFile) throws IOException, InterruptedException {
        return proxyPasswordParam(configFile, "user");
    }

    private static String proxyPassword(String configFile) throws IOException, InterruptedException {
        return proxyPasswordParam(configFile, "password");
    }

    private static boolean mightContainsProxyConfig(Path configFile) throws IOException {
        byte[] content = Files.readAllBytes(configFile);
        String strContent = new String(content, StandardCharsets.UTF_8);
        return strContent.contains("\"httpProxy\"");
    }

    static boolean mightContainMirrors(Path configFile) throws IOException {
        byte[] content = Files.readAllBytes(configFile);
        String strContent = new String(content, StandardCharsets.UTF_8);
        return strContent.contains("\"repositories\"") && strContent.contains("\"mirrors\"");
    }

    public static String repositoriesCredentials(String configFile) throws IOException, InterruptedException {

        Process proc = new ProcessBuilder(csCommand(), "config", "repositories.credentials", "--config-file", configFile)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();

        byte[] output = readAllBytes(proc.getInputStream());
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            System.err.println("Warning: failed to read repositories credentials from " + configFile + ", ignoring it.");
            printVersionMessage();
            return "";
        }
        return new String(output, StandardCharsets.UTF_8).trim();
    }

    public static boolean mightContainRepositoriesCredentials(Path configFile) throws IOException {
        if (!Files.exists(configFile))
          return false;
        byte[] content = Files.readAllBytes(configFile);
        String strContent = new String(content, StandardCharsets.UTF_8);
        return strContent.contains("\"repositories\"") && strContent.contains("\"credentials\"");
    }

    public static void maybeLoadConfig() throws Throwable {
        Path configPath = CoursierPaths.scalaConfigFile();

        if (Files.exists(configPath) && mightContainsProxyConfig(configPath)) {

            String address = proxyAddress(configPath.toString());

            if (!address.isEmpty()) {
                String user = proxyUser(configPath.toString());
                String password = proxyPassword(configPath.toString());

                SetupProxy.setProxyProperties(address, user, password, "");
                SetupProxy.setupAuthenticator();
            }
        }
    }

}
