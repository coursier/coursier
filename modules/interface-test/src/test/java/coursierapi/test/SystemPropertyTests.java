package coursierapi.test;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SystemPropertyTests {

    private static String javaCommand() {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("windows");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        return javaHome.resolve("bin").resolve(isWindows ? "java.exe" : "java").toString();
    }

    private static List<String> run(String property, String value, String mainClass) throws Exception {

        // passed by the build, as we can't reliably get our own class path here
        String classPath = System.getProperty("coursier-interface.test-classpath");
        assertNotNull("coursier-interface.test-classpath not set", classPath);

        List<String> command = new ArrayList<>();
        command.add(javaCommand());
        command.add("-cp");
        command.add(classPath);
        command.add("-D" + property + "=" + value);
        command.add(mainClass);

        ProcessBuilder builder = new ProcessBuilder(command);
        // environment variables take precedence over Java properties
        builder.environment().remove("COURSIER_REPOSITORIES");
        builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Path output = Files.createTempFile("coursier-interface-test-", ".txt");
        try {
            builder.redirectOutput(output.toFile());
            Process p = builder.start();
            int retCode = p.waitFor();
            assertEquals("Non-zero exit code from " + mainClass, 0, retCode);
            return Files.readAllLines(output);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    // the shading used to rename the coursier.repositories property under the hood,
    // see https://github.com/coursier/interface/issues/477
    @Test
    public void repositories() throws Exception {

        String repository = "https://foo.example.com/maven";

        // in a JVM of its own, as coursier only reads that property once
        List<String> defaultRepositories = run(
                "coursier.repositories",
                repository,
                PrintDefaultRepositories.class.getName()
        );

        assertEquals(java.util.Collections.singletonList(repository), defaultRepositories);
    }

}
