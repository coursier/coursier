package coursier.paths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Jep {

  private static boolean existsInPath(String exec) {
    // https://stackoverflow.com/questions/934191/how-to-check-existence-of-a-program-in-the-path/23539220#23539220
    return Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
      .map(Paths::get)
      .anyMatch(path -> Files.exists(path.resolve(exec)));
  }

  // https://stackoverflow.com/questions/309424/how-do-i-read-convert-an-inputstream-into-a-string-in-java/309718#309718
  private static String readFully(final InputStream is, final Charset charset, final int bufferSize) throws IOException {
     final char[] buffer = new char[bufferSize];
     final StringBuilder out = new StringBuilder();
     try (Reader in = new InputStreamReader(is, charset)) {
         for (;;) {
             int rsz = in.read(buffer, 0, buffer.length);
             if (rsz < 0)
                 break;
             out.append(buffer, 0, rsz);
         }
     }
     return out.toString();
  }

  public static class JepException extends Exception {
    public JepException(String message) {
      super(message);
    }
  }

  private final static String locationLinePrefix = "Location: ";

  private static String callProcess(Map<String, String> env, String... command) throws Exception {
    ProcessBuilder b = new ProcessBuilder(command)
      .redirectInput(ProcessBuilder.Redirect.PIPE)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.INHERIT);

    Map<String, String> processEnv = b.environment();
    env.forEach((k, v) -> processEnv.put(k, v));

    Process p = b.start();
    p.getOutputStream().close(); // close sub-process stdin

    String output = readFully(p.getInputStream(), Charset.defaultCharset(), 1024);
    int retValue = p.waitFor();
    if (retValue != 0) {
      if (!output.isEmpty())
        output = System.lineSeparator() + output;
      throw new JepException("Error running " + String.join(" ", command) + " (return code: " + retValue + ")" + output);
    }

    return output.trim();
  }

  private static String callProcess(String... command) throws Exception {
    return callProcess(Collections.emptyMap(), command);
  }

  public static File location() throws Exception {

    String fromEnv = System.getenv("JEP_LOCATION");
    if (fromEnv != null && !fromEnv.isEmpty())
      return new File(fromEnv);

    String fromProps = System.getProperty("jep.location");
    if (fromProps != null && !fromProps.isEmpty())
      return new File(fromProps);

    String pip = "pip";
    if (existsInPath("pip3"))
      pip = "pip3";

    String output = callProcess(pip, "show", "jep");

    Optional<String> locationOpt = Stream.of(output.split(System.getProperty("line.separator")))
      .filter(line -> line.startsWith(locationLinePrefix))
      .findFirst();
    if (!locationOpt.isPresent()) {
      throw new JepException("No location found in " + pip + " show jep output:" + output);
    }

    File base = new File(locationOpt.get().substring(locationLinePrefix.length()));

    return new File(base, "jep");
  }

  private final static String prefix = "jep-";
  private final static String suffix = ".jar";

  public static File jar(File jepDirectory) throws JepException {
    File[] jars = jepDirectory.listFiles(f -> f.isFile() && f.getName().startsWith(prefix) && f.getName().endsWith(suffix));
    if (jars == null || jars.length == 0)
      return null;
    else if (jars.length == 1)
      return jars[0];
    else {
      StringBuilder b = new StringBuilder("Found too many jars in " + jepDirectory + ": ");
      boolean isFirst = true;
      for (File f: jars) {
        if (isFirst)
          isFirst = false;
        else
          b.append(", ");
        b.append(f.getName());
      }
      throw new JepException(b.toString());
    }
  }

  public static String version(File jepJar) throws JepException {
    String name = jepJar.getName();
    if (!name.startsWith(prefix))
      throw new JepException("Invalid jep jar name: " + jepJar);
    if (!name.endsWith(suffix))
      throw new JepException("Invalid jep jar name: " + jepJar);
    String version = name.substring(prefix.length(), name.length() - suffix.length());
    return version;
  }

  private static String pythonExecutable() throws Exception {
    String fromEnv = System.getenv("PYTHONEXECUTABLE");
    if (fromEnv != null && !fromEnv.isEmpty() && existsInPath(fromEnv))
      return fromEnv;

    String fromProps = System.getProperty("python.executable");
    if (fromProps != null && !fromProps.isEmpty() && existsInPath(fromProps))
      return fromProps;

    if (existsInPath("python3"))
      return "python3";
    else if (existsInPath("python"))
      return "python";

    throw new JepException(
      "No existing Python executable found, either in PATH, in PYTHONEXECUTABLE environment variable or in python.executable system property."
    );
  }

  public static String pythonHome() throws Exception {

    String fromProps = System.getProperty("python.home");
    if (fromProps != null && !fromProps.isEmpty())
      return fromProps;

    Map<String, String> env = Collections.emptyMap();

    String fromEnv = System.getenv("PYTHONHOME");
    if (fromEnv != null && !fromEnv.isEmpty())
      env.put("PYTHONHOME", fromEnv);

    String python = pythonExecutable();

    return callProcess(env, python, "-c", "import sys;print(sys.exec_prefix)");
  }

  public static String pythonLDLibrary() throws Exception {
    String python = pythonExecutable();
    String cmd = "import sysconfig;print(sysconfig.get_config_var('LDVERSION'))";
    String pythonLDVersion = callProcess(python, "-c", cmd);

    return "python" + pythonLDVersion;
  }

  public static List<Map.Entry<String, String>> pythonProperties() throws Exception {
    String jnaLibraryPath = new File(new File(pythonHome()), "lib").getAbsolutePath();

    ArrayList<Map.Entry<String, String>> list = new ArrayList<>();

    list.add(new AbstractMap.SimpleEntry<>("jna.library.path", jnaLibraryPath));
    list.add(new AbstractMap.SimpleEntry<>("jna.nosys", "false"));

    return list;
  }

}
