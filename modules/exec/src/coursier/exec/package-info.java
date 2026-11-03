/**
 * Process-level system calls, that the JDK doesn't offer, for Linux and macOS.
 *
 * <ul>
 *   <li>{@link coursier.exec.Execve} replaces the current process by another program
 *       ({@code execve}),</li>
 *   <li>{@link coursier.exec.Chdir} changes the working directory of the current process
 *       ({@code chdir}).</li>
 * </ul>
 *
 * <p>These work both on the JVM, where they call the C library via JNA, and in GraalVM native
 * images, where GraalVM substitutions make direct system calls instead (so that JNA isn't
 * needed there). Failed system calls throw {@link coursier.exec.ErrnoException}.
 *
 * <p>On Windows, and other operating systems, the {@code available} methods of these classes
 * return false, and the other methods shouldn't be called.
 */
package coursier.exec;
