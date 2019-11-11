package coursier.paths;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class Mirror {

  public final static class Types {
    public static final String MAVEN = "maven";
    public static final String TREE = "tree";
  }

  public static Mirror of(List<String> from, String to, String type) {
    return new Mirror(from, to, type);
  }

  public static File defaultConfigFile() throws IOException {

    String fromEnv = System.getenv("COURSIER_MIRRORS");
    if (fromEnv != null) {
      return new File(fromEnv);
    }

    String fromProps = System.getProperty("coursier.mirrors");
    if (fromProps != null) {
      return new File(fromProps);
    }

    File configDir = coursier.paths.CoursierPaths.configDirectory();
    File propFile = new File(configDir, "mirror.properties");
    return propFile;
  }

  public static File extraConfigFile() throws IOException {

    String fromEnv = System.getenv("COURSIER_EXTRA_MIRRORS");
    if (fromEnv != null) {
      return new File(fromEnv);
    }

    String fromProps = System.getProperty("coursier.mirrors.extra");
    if (fromProps != null) {
      return new File(fromProps);
    }

    return null;
  }

  public static List<Mirror> load() throws MirrorPropertiesException, IOException {
    File propFile = defaultConfigFile();
    List<Mirror> mirrors = new ArrayList<>();

    if (propFile.exists())
      mirrors.addAll(parse(propFile));

    File extraPropFile = extraConfigFile();
    if (extraPropFile != null && extraPropFile.exists())
      mirrors.addAll(parse(extraPropFile));

    return Collections.unmodifiableList(mirrors);
  }

  public static List<Mirror> parse(File file) throws MirrorPropertiesException, IOException {
    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(file)) {
      props.load(fis);
    }

    List<Mirror> mirrors;
    try {
      mirrors = parse(props);
    } catch(MirrorPropertiesException ex) {
      throw new MirrorPropertiesException("Parsing " + file, ex);
    }

    return mirrors;
  }

  public static class MirrorPropertiesException extends Exception {
    public MirrorPropertiesException(String message) {
      super(message);
    }
    public MirrorPropertiesException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static List<Mirror> parse(Properties props) throws MirrorPropertiesException {

    List<String> toProps = new ArrayList<>();
    for (Object key: props.keySet()) {
      if (key instanceof String && ((String) key).endsWith(".to")) {
        toProps.add((String) key);
      }
    }

    List<Mirror> mirrors = new ArrayList<>();
    for (String toProp: toProps) {
      String prefix = toProp.substring(0, toProp.length() - 3);

      String rawFrom = props.getProperty(prefix + ".from");
      if (rawFrom == null) {
        throw new MirrorPropertiesException("Property " + prefix + ".from not found");
      }

      List<String> froms = new ArrayList<>(Arrays.asList(rawFrom.split(";")));
      for (String rawFrom0: rawFrom.split(";")) {
        if (!rawFrom0.isEmpty()) {
          if (rawFrom0.charAt(rawFrom0.length() - 1) == '/')
            froms.add(rawFrom0.substring(0, rawFrom0.length() - 1));
          else
            froms.add(rawFrom0);
        }
      }
      String to = props.getProperty(toProp);
      if (to.endsWith("/"))
        to = to.substring(0, to.length() - 1);
      String type = props.getProperty(prefix + ".type", Types.TREE);

      if (!Types.MAVEN.equals(type) && !Types.TREE.equals(type)) {
        throw new MirrorPropertiesException("Invalid value for property " + prefix + ".type");
      }

      mirrors.add(of(froms, to, type));
    }

    return mirrors;
  }


  private final List<String> from;
  private final String to;
  private final String type;

  private Mirror(List<String> from, String to, String type) {
    this.from = Collections.unmodifiableList(new ArrayList<>(from));
    this.to = to;
    this.type = type;
  }

  public List<String> from() {
    return from;
  }
  public String to() {
    return to;
  }
  public String type() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Mirror) {
      Mirror other = (Mirror) obj;
      return from.equals(other.from) && to.equals(other.to) && type.equals(other.type);
    }

    return false;
  }

  @Override
  public int hashCode() {
    int code = 17 + "Mirror".hashCode();
    code = 37 * code + from.hashCode();
    code = 37 * code + to.hashCode();
    code = 37 * code + type.hashCode();
    return 37 * code;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder("Mirror(");

    b.append("from=List(");
    boolean isFirst = true;
    for (String from0: from) {
      if (isFirst)
        isFirst = false;
      else
        b.append(", ");
      b.append(from0);
    }

    b.append("), to=");
    b.append(to);
    b.append(", type=");
    b.append(type);

    b.append(")");
    return b.toString();
  }


  public String transform(String url) {
    return matches(url, url);
  }

  public String matches(String url) {
    return matches(url, null);
  }

  public String matches(String url, String defaults) {
    for (String from0: from) {
      if (url.startsWith(from0 + "/")) {
        return to + url.substring(from0.length());
      }
    }

    return defaults;
  }

  public static String transform(List<Mirror> mirrors, String url) {
    for (Mirror mirror: mirrors) {
      String url0 = mirror.matches(url);
      if (url0 != null)
        return url0;
    }
    return url;
  }

}
