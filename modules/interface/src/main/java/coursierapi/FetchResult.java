package coursierapi;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class FetchResult implements Serializable {

    private List<Map.Entry<Artifact, File>> artifacts;
    private List<Dependency> dependencies;

    private FetchResult(List<Map.Entry<Artifact, File>> artifacts, List<Dependency> dependencies) {
        this.artifacts = Collections.unmodifiableList(new ArrayList<>(artifacts));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
    }

    public static FetchResult of(List<Map.Entry<Artifact, File>> artifacts) {
        return new FetchResult(artifacts, Collections.emptyList());
    }

    public static FetchResult of(List<Map.Entry<Artifact, File>> artifacts, List<Dependency> dependencies) {
        return new FetchResult(artifacts, dependencies);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof FetchResult) {
            FetchResult other = (FetchResult) obj;
            return this.artifacts.equals(other.artifacts) &&
                    this.dependencies.equals(other.dependencies);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (17 + artifacts.hashCode())) + dependencies.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("FetchResult(artifacts=[");
        boolean isFirst = true;
        for (Map.Entry<Artifact, File> entry : artifacts) {
            if (isFirst) {
                isFirst = false;
            } else {
                b.append(", ");
            }

            b.append(entry.getKey().toString());
            b.append(": ");
            b.append(entry.getValue().toString());
        }
        b.append("], dependencies=[");

        isFirst = true;
        for (Dependency dependency : dependencies) {
            if (isFirst) {
                isFirst = false;
            } else {
                b.append(", ");
            }

            b.append(dependency.toString());
        }
        b.append("])");

        return b.toString();
    }

    public List<Map.Entry<Artifact, File>> getArtifacts() {
        return artifacts;
    }

    public List<File> getFiles() {
        ArrayList<File> l = new ArrayList<>();
        for (Map.Entry<Artifact, File> entry : artifacts) {
            l.add(entry.getValue());
        }
        return l;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }
}
