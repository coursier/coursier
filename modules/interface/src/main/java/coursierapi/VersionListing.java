package coursierapi;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class VersionListing {

    private final String latest;
    private final String release;
    private final List<String> available;
    private final LocalDateTime lastUpdated;

    public static VersionListing of(String latest, String release, List<String> available, LocalDateTime lastUpdated) {
        return new VersionListing(latest, release, available, lastUpdated);
    }

    private VersionListing(String latest, String release, List<String> available, LocalDateTime lastUpdated) {
        this.latest = latest;
        this.release = release;
        this.available = Collections.unmodifiableList(new ArrayList<>(available));
        this.lastUpdated = lastUpdated;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof VersionListing) {
            VersionListing other = (VersionListing) obj;
            return this.latest.equals(other.latest) && this.release.equals(other.release) && this.available.equals(other.available) && Objects.equals(this.lastUpdated, other.lastUpdated);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (17 + latest.hashCode()) + release.hashCode()) + available.hashCode()) + Objects.hashCode(lastUpdated);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("VersionListing(");
        b.append(latest);
        b.append(", ");
        b.append(release);
        b.append(", available = List(");
        boolean isFirst = true;
        for (String ver : available) {
            if (isFirst)
                isFirst = false;
            else
                b.append(", ");
            b.append(ver);
        }
        b.append(")");
        if (lastUpdated != null) {
            b.append(", lastUpdated = ");
            b.append(lastUpdated.toString());
        }
        b.append(")");
        return b.toString();
    }

    public String getLatest() {
        return latest;
    }

    public String getRelease() {
        return release;
    }

    public List<String> getAvailable() {
        return available;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
}
