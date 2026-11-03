package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.util.Objects;

/**
 * Changes the working directory of the current process, with the
 * <a href="https://man7.org/linux/man-pages/man2/chdir.2.html">{@code chdir}</a> system call.
 *
 * <p>This is mostly useful right before {@link Execve#execve(String, String[], String[])}, so
 * that the new program starts in another directory:
 *
 * <pre>{@code
 * Chdir.chdir("/path/to/project");
 * Execve.execve("/usr/bin/git", new String[] {"git", "status"}, System.getenv());
 * }</pre>
 *
 * <p>Java itself doesn't notice the change: relative {@link java.io.File} or
 * {@link java.nio.file.Path} instances keep being resolved against the {@code user.dir}
 * Java property, which isn't updated, whereas sub-processes started afterwards and native
 * code see the new working directory. Avoid relative paths in Java code after calling this.
 *
 * <p>This only works on Linux and macOS, either on the JVM via JNA, or in GraalVM native images
 * via direct system calls. Check {@link #available()} first.
 */
public final class Chdir {

  /**
   * Creates an instance of this class
   *
   * @deprecated This class only has static methods, there's no need to instantiate it.
   */
  @Deprecated
  public Chdir() {}

  /**
   * JNA mapping of the C library {@code chdir} function
   *
   * @deprecated Not used by this module anymore, and only kept for binary compatibility. Use
   *             {@link Chdir#chdir(String)} instead.
   */
  @Deprecated
  public interface LibC extends Library {
    /** The C library, loaded via JNA */
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

  /**
   * Whether {@code chdir} can be called
   *
   * <p>This returns true on Linux and macOS, as long as the C library can be loaded (always
   * the case in native images, which don't rely on JNA).
   *
   * @return whether {@link #chdir(String)} can be called
   */
  public static boolean available() {
    return Libc.available();
  }

  /**
   * Changes the working directory of the current process
   *
   * @param path the new working directory, either absolute, or relative to the current
   *             working directory of the process
   * @throws ErrnoException       if the system call fails, such as when {@code path} doesn't
   *                              exist or isn't a directory (the cause of the exception is
   *                              then a {@link java.io.FileNotFoundException})
   * @throws NullPointerException if {@code path} is null
   */
  public static void chdir(String path) throws ErrnoException {
    Objects.requireNonNull(path, "path");
    chdir0(path);
  }

  // Substituted in native images, see coursier.exec.graalvm.ChdirGraalvm
  private static void chdir0(String path) throws ErrnoException {
    if (Libc.functions().chdir(path) != 0)
      throw Libc.exception("chdir " + path, path, Native.getLastError());
  }

}
