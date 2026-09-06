package coursierapi;

public final class ScalaVersion {

    private final String version;

    private ScalaVersion(String version) {
        this.version = version;
    }

    public static ScalaVersion of(String version) {
        // TODO Validate version a bit
        return new ScalaVersion(version);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ScalaVersion) {
            ScalaVersion other = (ScalaVersion) obj;
            return this.version.equals(other.version);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 17 + version.hashCode();
    }

    @Override
    public String toString() {
        return version;
    }

    public String getVersion() {
        return version;
    }
}
