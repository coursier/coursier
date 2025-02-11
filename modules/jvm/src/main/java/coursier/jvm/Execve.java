package coursier.jvm;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Locale;

// bits of this file were written with OpenAI o3-mini

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

  public static void execve(String path, String[] command, String[] env) throws ErrnoException {
    LibC.INSTANCE.execve(path, command, env);
  }

}