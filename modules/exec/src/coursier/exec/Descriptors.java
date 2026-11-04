package coursier.exec;

import java.io.File;
import java.util.Arrays;

/**
 * Decides which file descriptors a program started by {@link Execve} inherits.
 *
 * <p>The JDK opens most descriptors without the close-on-exec flag (files, jars of the class
 * path, sockets, pipes, …), and relies on {@link ProcessBuilder} closing all descriptors but
 * 0, 1, and 2 in sub-processes. {@code execve} doesn't do that, so we do it here instead:
 * only the standard input / output / error, and the descriptors passed explicitly, are
 * inherited.
 *
 * <p>Descriptors are marked close-on-exec rather than closed, so that the current process can
 * go on if {@code execve} fails.
 */
final class Descriptors {

  private Descriptors() {}

  /**
   * Checks the descriptors to pass to the new program
   *
   * @return the descriptors, sorted, without duplicates
   * @throws IllegalArgumentException if a descriptor is negative
   * @throws ErrnoException           if a descriptor isn't open
   */
  static int[] check(int[] inheritedFds) throws ErrnoException {
    int[] fds = inheritedFds.clone();
    Arrays.sort(fds);
    int count = 0;
    for (int i = 0; i < fds.length; i++) {
      int fd = fds[i];
      if (fd < 0)
        throw new IllegalArgumentException("Invalid file descriptor: " + fd);
      if (count > 0 && fds[count - 1] == fd)
        continue;
      int flags = Libc.getFdFlags(fd);
      if (flags < 0)
        throw new ErrnoException("fcntl " + fd, -flags, null);
      fds[count] = fd;
      count++;
    }
    return Arrays.copyOf(fds, count);
  }

  /**
   * Linux: marks all the descriptors of the current process close-on-exec, except 0, 1, 2,
   * and {@code inheritedFds}, from which that flag is removed
   *
   * @param inheritedFds as returned by {@link #check(int[])}
   */
  static void prepareExecve(int[] inheritedFds) throws ErrnoException {
    for (int fd : inheritedFds)
      if (fd > 2)
        setCloseOnExec(fd, false);

    for (int fd : openFds())
      if (fd > 2 && Arrays.binarySearch(inheritedFds, fd) < 0)
        setCloseOnExec(fd, true);
  }

  /**
   * macOS: the descriptors to pass to {@code posix_spawn_file_actions_addinherit_np}, that is
   * those of 0, 1, and 2 that are open, and {@code inheritedFds}
   *
   * @param inheritedFds as returned by {@link #check(int[])}
   */
  static int[] spawnInheritedFds(int[] inheritedFds) {
    int[] fds = new int[3 + inheritedFds.length];
    int count = 0;
    for (int fd = 0; fd <= 2; fd++)
      // inheriting a closed descriptor makes posix_spawn fail
      if (Libc.getFdFlags(fd) >= 0) {
        fds[count] = fd;
        count++;
      }
    for (int fd : inheritedFds)
      if (fd > 2) {
        fds[count] = fd;
        count++;
      }
    return Arrays.copyOf(fds, count);
  }

  private static void setCloseOnExec(int fd, boolean closeOnExec) throws ErrnoException {
    int flags = Libc.getFdFlags(fd);
    // EBADF: closed in the meantime, such as the descriptor that listed /proc/self/fd
    if (flags == -Libc.EBADF)
      return;
    if (flags < 0)
      throw new ErrnoException("fcntl " + fd, -flags, null);
    int newFlags = closeOnExec ? flags | Libc.FD_CLOEXEC : flags & ~Libc.FD_CLOEXEC;
    if (newFlags != flags) {
      int ret = Libc.setFdFlags(fd, newFlags);
      if (ret < 0 && ret != -Libc.EBADF)
        throw new ErrnoException("fcntl " + fd, -ret, null);
    }
  }

  // Without /proc, we check the descriptors below that, which should be enough in most cases
  private static final int fallbackFdLimit = 1024;

  /** The open descriptors of the current process, possibly with already closed ones */
  private static int[] openFds() {
    String[] names = new File("/proc/self/fd").list();
    if (names == null) {
      int[] fds = new int[fallbackFdLimit];
      for (int fd = 0; fd < fds.length; fd++)
        fds[fd] = fd;
      return fds;
    }
    int[] fds = new int[names.length];
    int count = 0;
    for (String name : names)
      try {
        fds[count] = Integer.parseInt(name);
        count++;
      } catch (NumberFormatException e) {
        // not a descriptor, ignored
      }
    return Arrays.copyOf(fds, count);
  }

}
