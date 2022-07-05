package coursier.bootstrap.launcher.credentials;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Java copy of coursier.credentials.FileCredentials
 */
public final class FileCredentials extends Credentials {

  private static final long serialVersionUID = 1L;

  private static final boolean DEFAULT_OPTIONAL = true;

  private final String path;
  private final boolean optional;

  public FileCredentials(String path, boolean optional) {
    this.path = Objects.requireNonNull(path);
    this.optional = optional;
  }

  public FileCredentials(String path) {
    this(path, DEFAULT_OPTIONAL);
  }

  @Override
  public List<DirectCredentials> get() throws IOException {
    final Path f = Paths.get(path);

    if (Files.isRegularFile(f)) {
      final String content = new String(Files.readAllBytes(f), Charset.defaultCharset());
      return parse(content, path);
    } else if (optional) {
      return Collections.emptyList();
    } else {
      throw new RuntimeException(String.format("Credentials file %s not found", path));
    }
  }

  public static List<DirectCredentials> parse(String content, String origin) throws IOException {
    final Properties props = new Properties();
    props.load(new StringReader(content));

    final List<String> userProps = props
      .stringPropertyNames()
      .stream()
      .filter(name -> name.endsWith(".username"))
      .collect(Collectors.toList());

    return userProps.stream().map(userProp -> {
      final String prefix = userProp.substring(0, userProp.length() - ".username".length());

      final String user = props.getProperty(userProp);
      final String password = Optional.ofNullable(props.getProperty(prefix + ".password"))
        .orElseThrow(() -> new RuntimeException(String.format("Property %s.password not found in %s", prefix, origin)));

      final String host = Optional.ofNullable(props.getProperty(prefix + ".host"))
        .orElseThrow(() -> new RuntimeException(String.format("Property %s.host not found in %s", prefix, origin)));

      final String realmOpt = props.getProperty(prefix + ".realm"); // fliter if empty?

      final boolean matchHost = Optional.ofNullable(props.getProperty(prefix + ".auto"))
        .map(Boolean::parseBoolean).orElse(DirectCredentials.DEFAULT_MATCH_HOST);
      final boolean httpsOnly = Optional.ofNullable(props.getProperty(prefix + ".https-only"))
        .map(Boolean::parseBoolean).orElse(DirectCredentials.DEFAULT_HTTPS_ONLY);
      final boolean passOnRedirect = Optional.ofNullable(props.getProperty(prefix + ".pass-on-redirect"))
        .map(Boolean::parseBoolean).orElse(DirectCredentials.DEFAULT_PASS_ON_REDIRECT);

      return new DirectCredentials(host, user, password, realmOpt)
        .withMatchHost(matchHost)
        .withHttpsOnly(httpsOnly)
        .withPassOnRedirect(passOnRedirect);
    }).collect(Collectors.toList());
  }

  public String getPath() {
    return path;
  }

  public boolean isOptional() {
    return optional;
  }

  @Override
  public String toString() {
    return "FileCredentials(" + path +
            ", " +
            optional +
            ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (optional ? 1231 : 1237);
    result = prime * result + ((path == null) ? 0 : path.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    FileCredentials other = (FileCredentials) obj;
    if (optional != other.optional) return false;
    if (path == null) {
      return other.path == null;
    } else return path.equals(other.path);
  }

}
