package coursierapi;

import java.io.Serializable;
import java.util.Objects;

public final class IvyRepository implements Repository, Serializable {

    private final String pattern;
    private String metadataPattern;
    private Credentials credentials;
    private boolean dropInfoAttributes;


    private IvyRepository(String pattern, String metadataPattern) {
        this.pattern = pattern;
        this.metadataPattern = metadataPattern;
        this.credentials = null;
        this.dropInfoAttributes = false;
    }


    public static IvyRepository of(String pattern, String metadataPattern) {
        return new IvyRepository(pattern, metadataPattern);
    }

    public static IvyRepository of(String pattern) {
        return new IvyRepository(pattern, null);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof IvyRepository) {
            IvyRepository other = (IvyRepository) obj;
            return this.pattern.equals(other.pattern) &&
                    Objects.equals(this.metadataPattern, other.metadataPattern) &&
                    Objects.equals(this.credentials, other.credentials) &&
                    this.dropInfoAttributes == other.dropInfoAttributes;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (17 + pattern.hashCode()) + Objects.hashCode(metadataPattern)) + Objects.hashCode(credentials)) + Boolean.hashCode(dropInfoAttributes);
    }

    @Override
    public String toString() {
        String credentialsString = "";
        if (credentials != null)
            credentialsString = ", " + credentials;
        String mdPatternString = "";
        if (metadataPattern != null)
            mdPatternString = ", " + metadataPattern;
        return "IvyRepository(" + pattern + mdPatternString + credentialsString + ", dropInfoAttributes = " + dropInfoAttributes + ")";
    }


    public String getPattern() {
        return pattern;
    }

    public String getMetadataPattern() {
        return metadataPattern;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public boolean getDropInfoAttributes() {
        return dropInfoAttributes;
    }

    public IvyRepository withMetadataPattern(String metadataPattern) {
        this.metadataPattern = metadataPattern;
        return this;
    }

    public IvyRepository withCredentials(Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public IvyRepository withDropInfoAttributes(boolean dropInfoAttributes) {
        this.dropInfoAttributes = dropInfoAttributes;
        return this;
    }
}
