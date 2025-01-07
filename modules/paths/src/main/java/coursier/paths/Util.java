package coursier.paths;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.alexarchambault.isterminal.IsTerminal;

public class Util {

    // No real reason to put that in the path module, except from the fact that that makes this accessible
    // from both the bootstrap-launcher and cli modules.

    private static final Pattern propertyRegex =
            Pattern.compile(Pattern.quote("${") + "[^" + Pattern.quote("{[()]}") + "]*" + Pattern.quote("}"));

    public static Map<String, String> expandProperties(Map<String, String> properties) {
        return expandProperties(System.getProperties(), properties);
    }
    public static Map<String, String> expandProperties(
        Properties systemProperties,
        Map<String, String> properties) {

        final Map<String, String> resolved = new LinkedHashMap<>(properties.size());
        final Map<String, String> withProps = new LinkedHashMap<>(properties.size());

        for (String k : properties.keySet()) {
            String value = properties.get(k);

            String actualKey = k;
            boolean process = true;

            if (k.endsWith("?")) {
                actualKey = k.substring(0, k.length() - 1);
                process = !systemProperties.containsKey(actualKey);
            }

            if (process) {
                Matcher matcher = propertyRegex.matcher(value);
                if (matcher.find()) {
                    withProps.put(actualKey, value);
                } else {
                    resolved.put(actualKey, value);
                }
            }
        }

        // we don't go recursive here - dynamic properties can only reference static ones

        for (String k : withProps.keySet()) {
            String value = withProps.get(k);

            Matcher matcher = propertyRegex.matcher(value);

            // cycles would loop indefinitely here :-|
            while (matcher.find()) {
                int start = matcher.start(0);
                int end = matcher.end(0);
                String subKey = value.substring(start + 2, end - 1);
                String subValue = resolved.get(subKey);
                if (subValue == null)
                    subValue = systemProperties.getProperty(subKey);
                if (subValue == null)
                    subValue = ""; // throw instead?
                value = value.substring(0, start) + subValue + value.substring(end);
                matcher = propertyRegex.matcher(value);
            }

            resolved.put(k, value);
        }

        return resolved;
    }

    public static void createDirectories(Path path) throws IOException {
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException ex) {
            // see https://bugs.openjdk.java.net/browse/JDK-8130464
            // Files.createDirectories does that check too, but with LinkOptions.NOFOLLOW_LINKS
            if (!Files.isDirectory(path))
                throw ex;
        }
    }

    private static volatile Boolean useColorOutput0 = null;
    private static boolean computeUseColorOutput() {

        if (System.getenv("INSIDE_EMACS") != null)
            return false;

        boolean disableViaEnv;
        String envProgress = System.getenv("COURSIER_PROGRESS");
        if (envProgress != null && (envProgress.equalsIgnoreCase("true") || envProgress.equalsIgnoreCase("enable") || envProgress.equalsIgnoreCase("1"))) {
            disableViaEnv = false;
        } else if (envProgress != null && (envProgress.equalsIgnoreCase("false") || envProgress.equalsIgnoreCase("disable") || envProgress.equalsIgnoreCase("0"))) {
            disableViaEnv = true;
        } else {
            disableViaEnv = System.getenv("COURSIER_NO_TERM") != null;
        }

        if (disableViaEnv)
            return false;

        return true;
    }

    // a bit more loose than useAnsiOutput (doesn't look at System.console() == null or System.getenv("CI"))
    public static boolean useColorOutput() {
        if (useColorOutput0 == null) {
            useColorOutput0 = computeUseColorOutput();
        }
        return useColorOutput0;
    }

    private static volatile Boolean useAnsiOutput0 = null;
    private static boolean computeUseAnsiOutput() {

        if (!IsTerminal.isTerminal())
            return false;

        if (System.getenv("INSIDE_EMACS") != null)
            return false;

        if (System.getenv("CI") != null)
            return false;

        boolean disableViaEnv;
        String envProgress = System.getenv("COURSIER_PROGRESS");
        if (envProgress != null && (envProgress.equalsIgnoreCase("true") || envProgress.equalsIgnoreCase("enable") || envProgress.equalsIgnoreCase("1"))) {
            disableViaEnv = false;
        } else if (envProgress != null && (envProgress.equalsIgnoreCase("false") || envProgress.equalsIgnoreCase("disable") || envProgress.equalsIgnoreCase("0"))) {
            disableViaEnv = true;
        } else {
            disableViaEnv = System.getenv("COURSIER_NO_TERM") != null;
        }

        if (disableViaEnv)
            return false;

        return true;
    }

    public static boolean useAnsiOutput() {
        if (useAnsiOutput0 == null) {
            useAnsiOutput0 = computeUseAnsiOutput();
        }
        return useAnsiOutput0;
    }

    private static Boolean useJni0 = null;
    public static boolean useJni() {
        return useJni(() -> {});
    }
    public static boolean useJni(Runnable beforeJni) {
        if (useJni0 != null)
          return useJni0;

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("windows");
        if (!isWindows) {
            useJni0 = false;
            return useJni0;
        }

        String prop = System.getenv("COURSIER_JNI");
        if (prop == null || prop.isEmpty())
          prop = System.getProperty("coursier.jni", "");

        boolean force = prop.equalsIgnoreCase("force");
        if (force) {
            beforeJni.run();
            useJni0 = true;
            return useJni0;
        }

        boolean disabled = prop.equalsIgnoreCase("false");
        if (disabled) {
            useJni0 = false;
            return useJni0;
        }

        // Try to get a dummy user env var from registry. If it fails, assume the JNI stuff is broken,
        // and fallback on PowerShell scripts.
        try {
            beforeJni.run();
            coursier.jniutils.WindowsEnvironmentVariables.get("PATH");
            useJni0 = true;
        } catch (Throwable t) {
            if (System.getProperty("coursier.jni.check.throw", "").equalsIgnoreCase("true"))
                throw new RuntimeException(t);
            useJni0 = false;
        }

        return useJni0;
    }
}
