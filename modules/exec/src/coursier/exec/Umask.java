package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Reads the umask of the current process
 *
 * The JVM doesn't expose it. On Linux, it's read from /proc/self/status. Elsewhere, or if that
 * fails, it's read with the umask system call (via JNA on the JVM, via GraalVM's C interface from
 * native images, see UmaskGraalvm).
 */
public final class Umask {

  private Umask() {}

  public interface LibC extends Library {
    LibC INSTANCE = Native.load("c", LibC.class);

    /**
     * Maps the umask system call:
     * mode_t umask(mode_t mask);
     *
     * mode_t is 32 bits wide on Linux, but only 16 bits wide on macOS, hence the masking of the
     * returned value by the callers.
     *
     * @param mask the new umask
     * @return the previous umask
     */
    int umask(int mask);
  }

  public static boolean available() {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    return osName.contains("linux") || osName.contains("mac");
  }

  /**
   * Sets the umask of the current process, and returns the previous one
   */
  public static int umask(int mask) {
    // Mainly ensuring LibC is initialized here
    if (LibC.INSTANCE == null)
      throw new RuntimeException("Should not happen");
    return LibC.INSTANCE.umask(mask) & 0777;
  }

  // Linux >= 4.7 lists the umask in /proc/self/status, as an octal 'Umask:' line
  private static Integer fromProcStatus() {
    Path status = Paths.get("/proc/self/status");
    if (!Files.isRegularFile(status))
      return null;
    List<String> lines;
    try {
      lines = Files.readAllLines(status, StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      return null;
    }
    for (String line : lines)
      if (line.startsWith("Umask:"))
        try {
          return Integer.parseInt(line.substring("Umask:".length()).trim(), 8) & 0777;
        }
        catch (NumberFormatException e) {
          return null;
        }
    return null;
  }

  /**
   * Returns the umask of the current process
   *
   * Preferably reads it from /proc on Linux. Elsewhere, there's no way to read the umask without
   * changing it, so this sets it to a restrictive value then restores it, which can briefly affect
   * files that other threads create in the meantime, so callers should call this once and keep
   * the result. Restoring it from a concurrent call would also be a problem, hence the
   * synchronization.
   *
   * @throws UnsupportedOperationException if not on Linux or macOS
   */
  public static synchronized int get() {
    if (!available())
      throw new UnsupportedOperationException("umask not available on this OS");
    Integer fromProc = fromProcStatus();
    if (fromProc != null)
      return fromProc;
    // 0077 rather than 0, so that files created by other threads in between end up more
    // restrictive than intended, rather than readable or writable by everyone
    int current = umask(0077);
    umask(current);
    return current;
  }

}
