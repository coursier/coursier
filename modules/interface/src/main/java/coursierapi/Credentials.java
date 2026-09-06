package coursierapi;

import java.io.Serializable;
import java.util.Objects;

public final class Credentials implements Serializable {

    private final String host;
    private final String user;
    private final String password;
    private final String realm;
    private final boolean optional;
    private final boolean matchHost;
    private final boolean httpsOnly;
    private final boolean passOnRedirect;

    private Credentials(
            String host,
            String user,
            String password,
            String realm,
            boolean optional,
            boolean matchHost,
            boolean httpsOnly,
            boolean passOnRedirect
    ) {
        this.host = host;
        this.user = user;
        this.password = password;
        this.realm = realm;
        this.optional = optional;
        this.matchHost = matchHost;
        this.httpsOnly = httpsOnly;
        this.passOnRedirect = passOnRedirect;
    }

    public static Credentials of(String user, String password) {
        return new Credentials("", user, password, null, true, true, false, false);
    }

    public static Credentials of(String host, String user, String password) {
        return new Credentials(host, user, password, null, true, true, false, false);
    }

    public static Credentials of(String host, String user, String password, String realm) {
        return new Credentials(host, user, password, realm, true, true, false, false);
    }

    public Credentials withHost(String host) {
        return new Credentials(host, this.user, this.password, this.realm, this.optional, this.matchHost, this.httpsOnly, this.passOnRedirect);
    }

    public Credentials withRealm(String realm) {
        return new Credentials(this.host, this.user, this.password, realm, this.optional, this.matchHost, this.httpsOnly, this.passOnRedirect);
    }

    public Credentials withOptional(boolean optional) {
        return new Credentials(this.host, this.user, this.password, this.realm, optional, this.matchHost, this.httpsOnly, this.passOnRedirect);
    }

    public Credentials withMatchHost(boolean matchHost) {
        return new Credentials(this.host, this.user, this.password, this.realm, this.optional, matchHost, this.httpsOnly, this.passOnRedirect);
    }

    public Credentials withHttpsOnly(boolean httpsOnly) {
        return new Credentials(this.host, this.user, this.password, this.realm, this.optional, this.matchHost, httpsOnly, this.passOnRedirect);
    }

    public Credentials withPassOnRedirect(boolean passOnRedirect) {
        return new Credentials(this.host, this.user, this.password, this.realm, this.optional, this.matchHost, this.httpsOnly, passOnRedirect);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof Credentials) {
            Credentials other = (Credentials) obj;
            return this.host.equals(other.host) &&
                    this.user.equals(other.user) &&
                    this.password.equals(other.password) &&
                    Objects.equals(this.realm, other.realm) &&
                    this.optional == other.optional &&
                    this.matchHost == other.matchHost &&
                    this.httpsOnly == other.httpsOnly &&
                    this.passOnRedirect == other.passOnRedirect;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = 17;
        h = 37 * h + host.hashCode();
        h = 37 * h + user.hashCode();
        h = 37 * h + password.hashCode();
        h = 37 * h + Objects.hashCode(realm);
        h = 37 * h + Boolean.hashCode(optional);
        h = 37 * h + Boolean.hashCode(matchHost);
        h = 37 * h + Boolean.hashCode(httpsOnly);
        h = 37 * h + Boolean.hashCode(passOnRedirect);
        return h;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Credentials(");
        if (!host.isEmpty()) {
            b.append("host=");
            b.append(host);
            b.append(", ");
        }
        b.append(user);
        b.append(", ****");
        if (realm != null) {
            b.append(", realm=");
            b.append(realm);
        }
        b.append(")");
        return b.toString();
    }

    public String getHost() {
        return host;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getRealm() {
        return realm;
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
}
