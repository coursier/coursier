package coursierapi;

import coursier.internal.api.ApiHelper;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Module implements Serializable {

    private final String organization;
    private final String name;
    private final Map<String, String> attributes;


    private Module(String organization, String name, Map<String, String> attributes) {
        this.organization = organization;
        this.name = name;
        this.attributes = new HashMap<>(attributes);
    }

    public static Module of(String organization, String name) {
        return new Module(organization, name, Collections.emptyMap());
    }

    public static Module of(String organization, String name, Map<String, String> attributes) {
        return new Module(organization, name, attributes);
    }

    public static Module parse(String mod, ScalaVersion scalaVersion) {
        return ApiHelper.parseModule(mod, scalaVersion.getVersion());
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof Module) {
            Module other = (Module) obj;
            return this.organization.equals(other.organization) &&
                    this.name.equals(other.name) &&
                    this.attributes.equals(other.attributes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (17 + organization.hashCode()) + name.hashCode()) + attributes.hashCode();
    }

    private String attributesString() {
        StringBuilder b = new StringBuilder();
        // TODO sort per attribute keys
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (b.length() != 0)
                b.append(", ");
            b.append(entry.getKey());
            b.append('=');
            b.append(entry.getValue());
        }
        return b.toString();
    }

    @Override
    public String toString() {
        String attributesString0 = attributesString();
        String attrSep;
        if (attributesString0.isEmpty())
            attrSep = "";
        else
            attrSep = ", ";
        return "Module(" + organization + ", " + name + attrSep + attributesString0 + ")";
    }

    public String getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

}
