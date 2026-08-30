package coursierapi;

import java.util.Objects;

public final class Artifact {

    private final String url;
    private final boolean changing;
    private final boolean optional;
    private final Credentials credentials;

    public static Artifact of(String url) {
        return new Artifact(url, false, false, null);
    }

    public static Artifact of(String url, boolean optional) {
        return new Artifact(url, false, optional, null);

    }

    public static Artifact of(String url, boolean changing, boolean optional) {
        return new Artifact(url, changing, optional, null);
    }

    public static Artifact of(String url, boolean changing, boolean optional, Credentials credentials) {
        return new Artifact(url, changing, optional, credentials);
    }

    private Artifact(String url, boolean changing, boolean optional, Credentials credentials) {
        this.url = url;
        this.changing = changing;
        this.optional = optional;
        this.credentials = credentials;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof Artifact) {
            Artifact other = (Artifact) obj;
            return this.url.equals(other.url) && this.changing == other.changing && this.optional == other.optional && Objects.equals(this.credentials, other.credentials);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (17 + url.hashCode()) + Boolean.hashCode(changing)) + Boolean.hashCode(optional)) + Objects.hashCode(credentials);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Artifact(");
        b.append(url);
        b.append(", optional = ");
        b.append(optional);
        b.append(", changing = ");
        b.append(changing);
        if (credentials != null) {
            b.append(", ");
            b.append(credentials.toString());
        }
        b.append(")");
        return b.toString();
    }

    public String getUrl() {
        return url;
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isOptional() {
        return optional;
    }

    public Credentials getCredentials() {
        return credentials;
    }
}
