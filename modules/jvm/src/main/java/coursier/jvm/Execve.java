package coursier.jvm;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.lang.reflect.Method;
import java.util.Locale;

// bits of this file were written with OpenAI o3-mini

@Deprecated
public final class Execve {

  public interface LibC extends Library {
    LibC INSTANCE = Native.load("c", LibC.class);

    /**
     * Maps the execve system call:
     * int execve(const char *filename, char *const argv[], char *const envp[]);
     *
     * @param filename the path to the executable
     * @param argv     the argument array (must be null-terminated)
     * @param envp     the environment variable array (must be null-terminated)
     * @return only returns on error (returns -1) because on success the process image is replaced.
     */
    int execve(String filename, String[] argv, String[] envp);
  }

  public static boolean available() {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    return osName.contains("linux") || osName.contains("mac");
  }

  private static void runShutdownHooks() {
    try {
      Class<?> shutdown = Class.forName("java.lang.Shutdown");
      Method runHooks = shutdown.getDeclaredMethod("runHooks");
      runHooks.setAccessible(true);
      synchronized (shutdown) {
        runHooks.invoke(null);
      }
    } catch (Exception e) {
      // ignore
    }
  }

  // Visible for GraalVM substitution in ExecveGraalvm
  static void execveNative(String path, String[] command, String[] env) throws ErrnoException {
    LibC.INSTANCE.execve(path, command, env);
  }

  public static void execve(String path, String[] command, String[] env) throws ErrnoException {
    runShutdownHooks();
    execveNative(path, command, env);
  }

}