package coursier.bootstrap.launcher.credentials;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Java copy of coursier.credentials.DirectCredentials
 */
public final class DirectCredentials extends Credentials {

  private static final long serialVersionUID = 1L;

  public static final boolean DEFAULT_MATCH_HOST = true;
  public static final boolean DEFAULT_HTTPS_ONLY = false;
  public static final boolean DEFAULT_PASS_ON_REDIRECT = false;

  private static final String DEFAULT_HOST = "";
  private static final boolean DEFAULT_OPTIONAL = true;

  private final String host;
  private final String usernameOpt;
  private final Password<String> passwordOpt;
  private final String realmOpt;
  private final boolean optional;
  private final boolean matchHost;
  private final boolean httpsOnly;
  private final boolean passOnRedirect;

  private DirectCredentials(
    String host,
    String username,
    Password<String> password,
    String realm,
    boolean optional,
    boolean matchHost,
    boolean httpsOnly,
    boolean passOnRedirect
  ) {
    this.host = (host != null) ? host : DEFAULT_HOST;
    this.usernameOpt = username;
    this.passwordOpt = password;
    this.realmOpt = realm;
    this.optional = optional;
    this.matchHost = matchHost;
    this.httpsOnly = httpsOnly;
    this.passOnRedirect = passOnRedirect;
  }

  public DirectCredentials(
    String host,
    String username,
    String password,
    String realm,
    boolean optional,
    boolean matchHost,
    boolean httpsOnly,
    boolean passOnRedirect
  ) {
    this(
      host,
      username,
      (password != null) ? new Password<>(password) : null,
      realm,
      optional,
      matchHost,
      httpsOnly,
      passOnRedirect);
  }

  public DirectCredentials(
    String host,
    String username,
    String password
  ) {
    this(
      host,
      username,
      password,
      null,
      DEFAULT_OPTIONAL,
      DEFAULT_MATCH_HOST,
      DEFAULT_HTTPS_ONLY,
      DEFAULT_PASS_ON_REDIRECT);
  }

  public DirectCredentials(
    String host,
    String username,
    String password,
    String realm
  ) {
    this(
      host,
      username,
      password,
      realm,
      DEFAULT_OPTIONAL,
      DEFAULT_MATCH_HOST,
      DEFAULT_HTTPS_ONLY,
      DEFAULT_PASS_ON_REDIRECT);
  }

  @Override
  public List<DirectCredentials> get() {
    return Collections.singletonList(this);
  }

  public String getHost() {
    return host;
  }

  public Optional<String> getUsernameOpt() {
    return Optional.ofNullable(usernameOpt);
  }

  public Optional<Password<String>> getPasswordOpt() {
    return Optional.ofNullable(passwordOpt);
  }

  public Optional<String> getRealmOpt() {
    return Optional.ofNullable(realmOpt);
  }

  public boolean isOptional() {
    return optional;
  }

  public boolean isMatchHost() {
    return matchHost;
  }

  public boolean isHttpsOnly() {
    return httpsOnly;
  }

  public boolean isPassOnRedirect() {
    return passOnRedirect;
  }

  public DirectCredentials withHost(String host) {
    return new DirectCredentials(
      host,
      this.usernameOpt,
      this.passwordOpt,
      this.realmOpt,
      this.optional,
      this.matchHost,
      this.httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withUsernameOpt(String username) {
    return new DirectCredentials(
      this.host,
      username,
      this.passwordOpt,
      this.realmOpt,
      this.optional,
      this.matchHost,
      this.httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withUsername(String username) {
    return withUsernameOpt(Objects.requireNonNull(username));
  }

  public DirectCredentials withPasswordOpt(String password) {
    return new DirectCredentials(
      this.host,
      this.usernameOpt,
      password,
      this.realmOpt,
      this.optional,
      this.matchHost,
      this.httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withPassword(String password) {
    return withPasswordOpt(Objects.requireNonNull(password));
  }

  public DirectCredentials withRealmOpt(String realm) {
    return new DirectCredentials(
      this.host,
      this.usernameOpt,
      this.passwordOpt,
      realm,
      this.optional,
      this.matchHost,
      this.httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withRealm(String realm) {
    return withRealmOpt(Objects.requireNonNull(realm));
  }

  public DirectCredentials withOptional(boolean optional) {
    return new DirectCredentials(
      this.host,
      this.usernameOpt,
      this.passwordOpt,
      this.realmOpt,
      optional,
      this.matchHost,
      this.httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withMatchHost(boolean matchHost) {
    return new DirectCredentials(
      this.host,
      this.usernameOpt,
      this.passwordOpt,
      this.realmOpt,
      this.optional,
      matchHost,
      this.httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withHttpsOnly(boolean httpsOnly) {
    return new DirectCredentials(
      this.host,
      this.usernameOpt,
      this.passwordOpt,
      this.realmOpt,
      this.optional,
      this.matchHost,
      httpsOnly,
      this.passOnRedirect
    );
  }

  public DirectCredentials withPassOnRedirect(boolean passOnRedirect) {
    return new DirectCredentials(
      this.host,
      this.usernameOpt,
      this.passwordOpt,
      this.realmOpt,
      this.optional,
      this.matchHost,
      this.httpsOnly,
      passOnRedirect
    );
  }

  @Override
  public String toString() {
    return "DirectCredentials(" + host +
            ", " +
            usernameOpt +
            ", " +
            passwordOpt +
            ", " +
            realmOpt +
            ", " +
            optional +
            ", " +
            matchHost +
            ", " +
            httpsOnly +
            ", " +
            passOnRedirect +
            ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((host == null) ? 0 : host.hashCode());
    result = prime * result + (httpsOnly ? 1231 : 1237);
    result = prime * result + (matchHost ? 1231 : 1237);
    result = prime * result + (optional ? 1231 : 1237);
    result = prime * result + (passOnRedirect ? 1231 : 1237);
    result = prime * result + ((passwordOpt == null) ? 0 : passwordOpt.hashCode());
    result = prime * result + ((realmOpt == null) ? 0 : realmOpt.hashCode());
    result = prime * result + ((usernameOpt == null) ? 0 : usernameOpt.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    DirectCredentials other = (DirectCredentials) obj;
    if (host == null) {
      if (other.host != null) return false;
    } else if (!host.equals(other.host)) return false;
    if (httpsOnly != other.httpsOnly) return false;
    if (matchHost != other.matchHost) return false;
    if (optional != other.optional) return false;
    if (passOnRedirect != other.passOnRedirect) return false;
    if (passwordOpt == null) {
      if (other.passwordOpt != null) return false;
    } else if (!passwordOpt.equals(other.passwordOpt)) return false;
    if (realmOpt == null) {
      if (other.realmOpt != null) return false;
    } else if (!realmOpt.equals(other.realmOpt)) return false;
    if (usernameOpt == null) {
      return other.usernameOpt == null;
    } else return usernameOpt.equals(other.usernameOpt);
  }
 
}
