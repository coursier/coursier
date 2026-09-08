package coursierapi;

import java.io.Serializable;

public final class Publication implements Serializable {

    private final String name;
    private final String type;
    private final String extension;
    private final String classifier;

    public Publication(String name, String type, String extension, String classifier) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.classifier = classifier;
    }

    public Publication(String name) {
        this(name, "", "", "");
    }

    public Publication(String name, String type) {
        this(name, type, "", "");
    }

    public Publication(String name, String type, String extension) {
        this(name, type, extension, "");
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getExtension() {
        return extension;
    }

    public String getClassifier() {
        return classifier;
    }

    public Publication withName(String updatedName) {
        return new Publication(updatedName, this.type, this.extension, this.classifier);
    }

    public Publication withType(String updatedType) {
        return new Publication(this.name, updatedType, this.extension, this.classifier);
    }

    public Publication withExtension(String updatedExtension) {
        return new Publication(this.name, this.type, updatedExtension, this.classifier);
    }

    public Publication withClassifier(String updatedClassifier) {
        return new Publication(this.name, this.type, this.extension, updatedClassifier);
    }

    public boolean isEmpty() {
        return name.isEmpty() && type.isEmpty() && extension.isEmpty() && classifier.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof Publication) {
            Publication other = (Publication) o;
            return name.equals(other.name) &&
                   type.equals(other.type) &&
                   extension.equals(other.extension) &&
                   classifier.equals(other.classifier);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (17 + extension.hashCode()) + classifier.hashCode()) + type.hashCode()) + name.hashCode());
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Publication(");
        b.append(name);
        b.append(", ");
        if (!type.isEmpty()) {
            b.append(", type=");
            b.append(type);
        }
        if (!classifier.isEmpty()) {
            b.append(", classifier=");
            b.append(classifier);
        }
        if (extension != null) {
            b.append(", extension=");
            b.append(extension);
        }
        b.append(")");
        return b.toString();
    }
}
