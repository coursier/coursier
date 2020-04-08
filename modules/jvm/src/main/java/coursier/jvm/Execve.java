package coursier.jvm;

public final class Execve {

  public static boolean available() {
    return false;
  }

  public static void execve(String path, String[] command, String[] env) throws ErrnoException {
    // Not supported on the JVM, returning immediately
  }

}