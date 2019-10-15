package coursier.paths;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    // No real reason to put that in the path module, except from the fact that that makes this accessible
    // from both the bootstrap-launcher and cli modules.

    private static final Pattern propertyRegex =
            Pattern.compile(Pattern.quote("${") + "[^" + Pattern.quote("{[()]}") + "]*" + Pattern.quote("}"));

    public static Map<String, String> expandProperties(Map<String, String> properties) {

        final Map<String, String> resolved = new LinkedHashMap<>(properties.size());
        final Map<String, String> withProps = new LinkedHashMap<>(properties.size());

        for (String k : properties.keySet()) {
            String value = properties.get(k);

            Matcher matcher = propertyRegex.matcher(value);
            if (matcher.find()) {
                withProps.put(k, value);
            } else {
                resolved.put(k, value);
            }
        }

        // we don't go recursive here - dynamic properties can only reference static ones

        for (String k : withProps.keySet()) {
            String value = properties.get(k);

            Matcher matcher = propertyRegex.matcher(value);

            // cycles would loop indefinitely here :-|
            while (matcher.find()) {
                int start = matcher.start(0);
                int end = matcher.end(0);
                String subKey = value.substring(start + 2, end - 1);
                String subValue = resolved.get(subKey);
                if (subValue == null)
                    subValue = System.getProperty(subKey);
                if (subValue == null)
                    subValue = ""; // throw instead?
                value = value.substring(0, start) + subValue + value.substring(end);
            }

            resolved.put(k, value);
        }

        return resolved;
    }

    public static void createDirectories(Path path) throws IOException {
        try {
            Files.createDirectories(path);
        } catch (FileAlreadyExistsException ex) {
            // Files.createDirectories does that check too, but with LinkOptions.NOFOLLOW_LINKS
            if (!Files.isDirectory(path))
                throw ex;
        }
    }
}
