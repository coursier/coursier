package coursier.jvm;

@Deprecated
public final class Execve {

  public static boolean available() {
    return coursier.exec.Execve.available();
  }

  public static void execve(String path, String[] command, String[] env) throws ErrnoException {
    try {
      coursier.exec.Execve.execve(path, command, env);
    } catch (coursier.exec.ErrnoException e) {
      throw new ErrnoException(e.value(), e.getCause());
    }
  }

}
