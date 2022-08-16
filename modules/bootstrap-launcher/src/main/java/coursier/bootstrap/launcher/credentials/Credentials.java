package coursier.bootstrap.launcher.credentials;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java copy of coursier.credentials.Credentials
 */
public abstract class Credentials implements Serializable {

  private static final long serialVersionUID = 1L;

  public abstract List<DirectCredentials> get() throws IOException;

  /**
   * Java copy of coursier.cache.CacheDefaults::credentials
   */
  public static List<Credentials> credentials() throws IOException {
    if (!credentialPropOpt().isPresent()) {
      // Warn if those files have group and others read permissions?
      final List<File> configDirs = Arrays.asList(coursier.paths.CoursierPaths.configDirectories());
      final List<File> mainCredentialsFiles = configDirs.stream()
          .map(configDir -> new File(configDir, "credentials.properties")).collect(Collectors.toList());
      final List<FileCredentials> otherFiles;
      {
        // delay listing files until credentials are really needed?
        final List<File> dirs = configDirs.stream().map(configDir -> new File(configDir, "credentials")).collect(Collectors.toList());
        final List<File> files = dirs.stream().flatMap(dir -> Optional.ofNullable(dir.listFiles((dir1, name) -> !name.startsWith(".") && name.endsWith(".properties"))).map(Arrays::stream).orElse(Stream.empty())).collect(Collectors.toList());
        otherFiles = files.stream().map(f -> new FileCredentials(f.getAbsolutePath(), true)).collect(Collectors.toList());
      }
      final List<Credentials> credentials = mainCredentialsFiles.stream().map(f -> new FileCredentials(f.getAbsolutePath(), true)).collect(Collectors.<Credentials>toList());
      credentials.addAll(otherFiles);
      return credentials;
    } else {
      return credentialPropOpt()
        .filter(Credentials::isPropFile)
        .map(s -> {
          if (isPropFile(s)) {
            try {
              final String path0 = s.startsWith("file:") ? new File(new URI(s)).getAbsolutePath() : s;
              return Collections.<Credentials>singletonList(new FileCredentials(path0, true));
            } catch (URISyntaxException e) {
              return Collections.<Credentials>emptyList();
            }
          } else {
            return new ArrayList<Credentials>(CredentialsParser.parseList(s));
          }
        })
        .orElseGet(Collections::emptyList);
    }
  }

  /**
   * Java copy of coursier.cache.CacheDefaults::credentialPropOpt
   */
  private static Optional<String> credentialPropOpt() {
    return Optional.ofNullable(System.getenv("COURSIER_CREDENTIALS"))
      .map(Optional::of).orElse(Optional.ofNullable(System.getProperty("coursier.credentials"))) // Java 9: .or(() -> Optional.ofNullable(System.getProperty("coursier.credentials")))
      .map(s -> s.replaceFirst("^\\s+", ""));
  }

  /**
   * Java copy of coursier.cache.CacheDefaults::isPropFile
   */
  private static boolean isPropFile(String s) {
    return s.startsWith("/") || s.startsWith("file:");
  }

}
