package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Locale;

public final class Chdir {

  public interface LibC extends Library {
    LibC INSTANCE = Native.load("c", LibC.class);

    /**
     * Maps the chdir system call:
     * int chdir(const char *path);
     *
     * @param path the path to change the current working directory to
     * @return 0 on success, -1 on error (errno is set)
     */
    int chdir(String path);
  }

  public static boolean available() {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    return osName.contains("linux") || osName.contains("mac");
  }

  public static void chdir(String path) throws ErrnoException {
    // Mainly ensuring LibC is initialized here
    if (LibC.INSTANCE == null)
      throw new RuntimeException("Should not happen");
    int ret = LibC.INSTANCE.chdir(path);
    // Not building an ErrnoException here, as the latter relies on GraalVM classes
    // that aren't available on the JVM (see ErrnoException / the GraalVM substitution).
    if (ret != 0)
      throw new RuntimeException("chdir to " + path + " failed (errno " + Native.getLastError() + ")");
  }

}
