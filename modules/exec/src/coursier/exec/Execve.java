package coursier.exec;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

// bits of this file were written with OpenAI o3-mini

/**
 * Replaces the current process by another program, with the
 * <a href="https://man7.org/linux/man-pages/man2/execve.2.html">{@code execve}</a> system call.
 *
 * <p>Unlike starting a sub-process, the new program keeps the PID of the current process, and
 * inherits its standard input / output / error, its working directory (see {@link Chdir}), and
 * the terminal it runs in, if any. As the current process is gone once the new program starts,
 * nothing can be run after it exits: use a sub-process (such as {@link ProcessBuilder}) if you
 * need to clean things up or check the exit code of the new program.
 *
 * <p>This only works on Linux and macOS, either on the JVM via JNA, or in GraalVM native images
 * via direct system calls. Check {@link #available()} first, and fall back to running a
 * sub-process if it returns false:
 *
 * <pre>{@code
 * String[] command = {"/usr/bin/java", "-version"};
 * if (Execve.available())
 *   Execve.execve(command[0], command, System.getenv()); // only returns by throwing
 * else {
 *   int exitCode = new ProcessBuilder(command).inheritIO().start().waitFor();
 *   System.exit(exitCode);
 * }
 * }</pre>
 *
 * <p>The shutdown hooks of the current process are run right before the system call, so that
 * they're not lost (see {@link #runShutdownHooks()}).
 */
public final class Execve {

  /**
   * Creates an instance of this class
   *
   * @deprecated This class only has static methods, there's no need to instantiate it.
   */
  @Deprecated
  public Execve() {}

  /**
   * JNA mapping of the C library {@code execve} function
   *
   * @deprecated Not used by this module anymore, and only kept for binary compatibility. Use
   *             {@link Execve#execve(String, String[], String[])} instead.
   */
  @Deprecated
  public interface LibC extends Library {
    /** The C library, loaded via JNA */
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

  /**
   * Whether {@code execve} can be called
   *
   * <p>This returns true on Linux and macOS, as long as the C library can be loaded (always
   * the case in native images, which don't rely on JNA).
   *
   * @return whether the {@code execve} methods of this class can be called
   */
  public static boolean available() {
    return Libc.available();
  }

  /**
   * Runs the shutdown hooks of the current JVM, registered with
   * {@link Runtime#addShutdownHook(Thread)}, and waits for them to complete
   *
   * <p>The {@code execve} methods of this class call this before replacing the current
   * process, as the JVM doesn't run shutdown hooks itself in that case. There's no need to
   * call it beforehand.
   *
   * <p>Once it has been called, the JVM considers that it's shutting down: adding or removing
   * shutdown hooks throws {@link IllegalStateException}, and the hooks are not run again when
   * the JVM exits.
   *
   * <p>On the JVM, this relies on internal JDK APIs. These need
   * {@code --add-opens java.base/java.lang=ALL-UNNAMED} to be passed to {@code java} on Java
   * 16 and later, and the shutdown hooks are silently not run if it isn't.
   */
  // public so that the GraalVM substitution in coursier.exec.graalvm can fall back to it
  public static void runShutdownHooks() {
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

  /**
   * Replaces the current process by another program
   *
   * <p>Unlike shells, this doesn't look for {@code path} in the {@code PATH} directories: it
   * must be an absolute path, or a path relative to the current working directory.
   *
   * <p>This runs the shutdown hooks of the current process before the system call (see
   * {@link #runShutdownHooks()}). If the system call fails, they're not run again: the
   * process should exit right after this method throws.
   *
   * @param path    path to the executable to run
   * @param command arguments of the new program, starting with its name, usually
   *                {@code path} itself, that becomes its {@code argv[0]}
   * @param env     environment of the new program, as {@code "NAME=value"} entries
   * @throws ErrnoException       if the system call fails, such as when {@code path} doesn't
   *                              exist or isn't executable. This method never returns
   *                              normally.
   * @throws NullPointerException if any of the arguments, or any of their elements, is null
   *                              (checked before running the shutdown hooks)
   */
  public static void execve(String path, String[] command, String[] env) throws ErrnoException {
    Objects.requireNonNull(path, "path");
    checkNoNullElements(command, "command");
    checkNoNullElements(env, "env");
    execve0(path, command, env);
  }

  /**
   * Replaces the current process by another program
   *
   * <p>See {@link #execve(String, String[], String[])} for more details.
   *
   * @param path    path to the executable to run
   * @param command arguments of the new program, starting with its name, usually
   *                {@code path} itself, that becomes its {@code argv[0]}
   * @param env     environment of the new program, such as {@link System#getenv()}
   * @throws ErrnoException       if the system call fails. This method never returns normally.
   * @throws NullPointerException if any of the arguments, or any of their elements, is null
   */
  public static void execve(
    String path,
    String[] command,
    Map<String, String> env
  ) throws ErrnoException {
    Objects.requireNonNull(env, "env");
    String[] env0 = new String[env.size()];
    int i = 0;
    for (Map.Entry<String, String> entry : env.entrySet()) {
      String name  = Objects.requireNonNull(entry.getKey(), "environment variable name");
      String value = Objects.requireNonNull(entry.getValue(), "value of " + name);
      env0[i] = name + "=" + value;
      i++;
    }
    Arrays.sort(env0);
    execve(path, command, env0);
  }

  /**
   * Replaces the current process by another program, that inherits the environment of the
   * current process
   *
   * <p>See {@link #execve(String, String[], String[])} for more details.
   *
   * @param path    path to the executable to run
   * @param command arguments of the new program, starting with its name, usually
   *                {@code path} itself, that becomes its {@code argv[0]}
   * @throws ErrnoException       if the system call fails. This method never returns normally.
   * @throws NullPointerException if any of the arguments, or any of their elements, is null
   */
  public static void execve(String path, String[] command) throws ErrnoException {
    execve(path, command, System.getenv());
  }

  private static void checkNoNullElements(String[] array, String name) {
    Objects.requireNonNull(array, name);
    for (int i = 0; i < array.length; i++)
      Objects.requireNonNull(array[i], name + "[" + i + "]");
  }

  // Substituted in native images, see coursier.exec.graalvm.ExecveGraalvm
  private static void execve0(String path, String[] command, String[] env) throws ErrnoException {
    // Loading the C library before running the shutdown hooks, so that these are only run
    // if we can call execve
    Libc.Functions libc = Libc.functions();
    runShutdownHooks();
    libc.execve(path, command, env);
    // execve only returns on error
    throw Libc.exception("execve " + path, path, Native.getLastError());
  }

}
