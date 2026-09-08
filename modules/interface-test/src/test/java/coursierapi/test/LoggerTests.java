package coursierapi.test;

import coursierapi.Cache;
import coursierapi.Dependency;
import coursierapi.Fetch;
import coursierapi.Logger;
import coursierapi.error.CoursierError;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class LoggerTests {

    // https://github.com/coursier/interface/issues/70
    @Test
    public void progressBarsFromPrintWriter() throws Exception {

        StringWriter buffer = new StringWriter();
        // an empty cache, so that the artifacts are actually downloaded, and progress is reported
        Path cacheLocation = Files.createTempDirectory("coursier-interface-logger-tests");

        try {
            Cache cache = Cache.create()
                    .withLocation(cacheLocation.toFile())
                    .withLogger(Logger.progressBars(new PrintWriter(buffer)));

            Fetch fetch = Fetch.create()
                    .withCache(cache)
                    .addDependencies(Dependency.of("org.slf4j", "slf4j-api", "2.0.9").withTransitive(false));

            try {
                fetch.fetch();
            } catch (CoursierError e) {
                throw new RuntimeException(e);
            }

            String output = buffer.toString();
            assertTrue(output, output.contains("slf4j-api-2.0.9.jar"));
        } finally {
            deleteRecursively(cacheLocation.toFile());
        }
    }

    private static void deleteRecursively(File f) {
        File[] content = f.listFiles();
        if (content != null) {
            for (File child : content) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }
}
