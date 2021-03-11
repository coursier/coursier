package coursier.bootstrap.launcher.credentials;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Java copy of coursier.parse.CredentialsParser
 */
public class CredentialsParser {

  private static final Pattern CREDENTIALS_PARSER_PATTERN = Pattern
      .compile("([a-zA-Z0-9.-]*)(\\(.*\\))?[ ]+([^ :][^:]*):(.*)");

  public static final Optional<DirectCredentials> parse(String s) {
    final Matcher m = CREDENTIALS_PARSER_PATTERN.matcher(s);
    if (m.matches()) {
      return Optional.of(new DirectCredentials(m.group(1), m.group(3), m.group(4))
        .withRealmOpt(Optional.ofNullable(m.group(2)).map(r -> r.replaceFirst("^\\(", "").replaceFirst("\\)$", "")).orElseGet(() -> null)));
    } else {
      return Optional.empty();
    }
  }

  public static final List<DirectCredentials> parseList(String input) {
    return Arrays.stream(input.split("\\r?\\n")) // Java 11: input.lines()
      .map(s -> s.replaceFirst("^\\s+", "")) // not trimming chars on the right (password)
      .filter(s -> !s.isEmpty())
      .map(CredentialsParser::parse)
      .filter(o -> o.isPresent())
      .map(o -> o.get())
      .collect(Collectors.toList());
  }

}
