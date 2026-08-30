package coursierapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VersionsResult {

    private final List<Map.Entry<Repository, String>> errors;
    private final List<Map.Entry<Repository, VersionListing>> listings;
    private final VersionListing mergedListings;

    private VersionsResult(List<Map.Entry<Repository, String>> errors, List<Map.Entry<Repository, VersionListing>> listings, VersionListing mergedListings) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.listings = Collections.unmodifiableList(new ArrayList<>(listings));
        this.mergedListings = mergedListings;
    }

    public static VersionsResult of(List<Map.Entry<Repository, String>> errors, List<Map.Entry<Repository, VersionListing>> listings, VersionListing mergedListings) {
        return new VersionsResult(errors, listings, mergedListings);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof VersionsResult) {
            VersionsResult other = (VersionsResult) obj;
            return this.errors.equals(other.errors) && this.listings.equals(other.listings) && this.mergedListings.equals(other.mergedListings);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (17 + errors.hashCode()) + listings.hashCode()) + mergedListings.hashCode());
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("VersionsResult(errors=[");
        boolean isFirst = true;
        for (Map.Entry<Repository, String> ent : errors) {
            if (isFirst) {
                isFirst = false;
            } else {
                b.append(", ");
            }

            b.append(ent.getKey());
            b.append(": ");
            b.append(ent.getValue());
        }
        b.append(", listings=[");
        for (Map.Entry<Repository, VersionListing> ent : listings) {
            if (isFirst) {
                isFirst = false;
            } else {
                b.append(", ");
            }

            b.append(ent.getKey());
            b.append(": ");
            b.append(ent.getValue());
        }
        b.append("], mergedListings=");
        b.append(mergedListings);
        b.append(")");
        return b.toString();
    }

    public List<Map.Entry<Repository, String>> getErrors() {
        return errors;
    }
    public List<Map.Entry<Repository, VersionListing>> getListings() {
        return listings;
    }
    public VersionListing getMergedListings() {
        return mergedListings;
    }
}
