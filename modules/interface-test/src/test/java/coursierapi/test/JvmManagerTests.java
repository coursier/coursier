package coursierapi.test;

import coursierapi.JvmManager;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import static org.junit.Assert.*;

public class JvmManagerTests {

    private static boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return osName.startsWith("windows");
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int read = -1;
        read = is.read(buf);
        while (read >= 0) {
            if (read > 0)
                baos.write(buf, 0, read);
            read = is.read(buf);
        }
        return baos.toByteArray();
    }

    @Test
    public void simple() throws IOException, InterruptedException {
        JvmManager jvmManager = JvmManager.create();
        File javaHome = jvmManager.get("zulu:18");

        String ext = "";
        if (isWindows())
            ext = ".exe";
        File javaBin = new File(javaHome, "bin/java" + ext);

        ProcessBuilder b = new ProcessBuilder(javaBin.getAbsolutePath(), "-version")
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true);
        Process p = b.start();
        byte[] rawOutput = readAllBytes(p.getInputStream());
        String output = new String(rawOutput);
        int exitCode = p.waitFor();
        if (exitCode != 0)
            System.err.println("Warning: " + javaBin + " -version command exited with code " + exitCode);

        assertTrue(output.contains("openjdk version \"18"));
        assertTrue(output.contains("Zulu18"));
    }
}
