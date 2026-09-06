package coursierapi;

import java.util.*;

public final class DependencyManagement {

    private DependencyManagement() {}

    public static final class Key {
        private final String organization;
        private final String name;
        private final String type;
        private final String classifier;

        public Key(String organization, String name, String type, String classifier) {
            this.organization = organization;
            this.name = name;
            this.type = type;
            this.classifier = classifier;
        }

        public String getOrganization() {
            return organization;
        }
        public String getName() {
            return name;
        }
        public String getType() {
            return type;
        }
        public String getClassifier() {
            return classifier;
        }

        public Key withOrganization(String newOrganization) {
            return new Key(newOrganization, name, type, classifier);
        }
        public Key withName(String newName) {
            return new Key(organization, newName, type, classifier);
        }
        public Key withType(String newType) {
            return new Key(organization, name, newType, classifier);
        }
        public Key withClassifier(String newClassifier) {
            return new Key(organization, name, type, newClassifier);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return Objects.equals(organization, key.organization) && Objects.equals(name, key.name) && Objects.equals(type, key.type) && Objects.equals(classifier, key.classifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(organization, name, type, classifier);
        }

        @Override
        public String toString() {
            return "Key{" +
                    "organization='" + organization + '\'' +
                    ", name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", classifier='" + classifier + '\'' +
                    '}';
        }
    }

    public static final class Values {
        private final String configuration;
        private final String version;
        private final Set<Map.Entry<String, String>> exclusions = new HashSet<>();
        private final boolean optional;

        public Values(String configuration, String version, boolean optional) {
            this.configuration = configuration;
            this.version = version;
            this.optional = optional;
        }

        public Values addExclusion(String organization, String name) {
            this.exclusions.add(new AbstractMap.SimpleImmutableEntry<>(organization, name));
            return this;
        }
        public Values addExclusions(Set<Map.Entry<String, String>> exclusions) {
            this.exclusions.addAll(exclusions);
            return this;
        }

        public String getConfiguration() {
            return configuration;
        }
        public String getVersion() {
            return version;
        }
        public Set<Map.Entry<String, String>> getExclusions() {
            return Collections.unmodifiableSet(exclusions);
        }
        public boolean isOptional() {
            return optional;
        }

        public Values withConfiguration(String newConfiguration) {
            Values newValues = new Values(newConfiguration, version, optional);
            newValues.addExclusions(this.exclusions);
            return newValues;
        }
        public Values withVersion(String newVersion) {
            Values newValues = new Values(configuration, newVersion, optional);
            newValues.addExclusions(this.exclusions);
            return newValues;
        }
        public Values withExclusions(Set<Map.Entry<String, String>> exclusions) {
            Values newValues = new Values(configuration, version, optional);
            newValues.addExclusions(exclusions);
            return newValues;
        }
        public Values withOptional(boolean newOptional) {
            Values newValues = new Values(configuration, version, newOptional);
            newValues.addExclusions(this.exclusions);
            return newValues;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Values)) return false;
            Values values = (Values) o;
            return optional == values.optional && Objects.equals(configuration, values.configuration) && Objects.equals(version, values.version) && Objects.equals(exclusions, values.exclusions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configuration, version, exclusions, optional);
        }

        @Override
        public String toString() {
            return "Values{" +
                    "configuration='" + configuration + '\'' +
                    ", version='" + version + '\'' +
                    ", exclusions=" + exclusions +
                    ", optional=" + optional +
                    '}';
        }
    }

}
