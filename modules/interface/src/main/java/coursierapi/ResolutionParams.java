package coursierapi;

import java.io.Serializable;
import java.util.*;

public class ResolutionParams implements Serializable {

    private Integer maxIterations;
    private final HashMap<Module, String> forceVersions;
    private final HashMap<String, String> forcedProperties;
    private final HashSet<String> profiles;
    private final HashSet<Map.Entry<String, String>> exclusions;
    private boolean useSystemOsInfo;
    private boolean useSystemJdkVersion;
    private String scalaVersion;
    private Boolean keepProvidedDependencies;
    private Boolean forceDepMgmtVersions;
    private Boolean enableDependencyOverrides;
    private String defaultConfiguration;

    private ResolutionParams() {
        maxIterations = null;
        forceVersions = new HashMap<>();
        forcedProperties = new HashMap<>();
        profiles = new HashSet<>();
        exclusions = new HashSet<>();
        useSystemOsInfo = true;
        useSystemJdkVersion = true;
        scalaVersion = null;
        keepProvidedDependencies = null;
        forceDepMgmtVersions = null;
        enableDependencyOverrides = null;
        defaultConfiguration = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolutionParams)) return false;
        ResolutionParams that = (ResolutionParams) o;
        return useSystemOsInfo == that.useSystemOsInfo &&
                useSystemJdkVersion == that.useSystemJdkVersion &&
                Objects.equals(maxIterations, that.maxIterations) &&
                Objects.equals(forceVersions, that.forceVersions) &&
                Objects.equals(forcedProperties, that.forcedProperties) &&
                Objects.equals(profiles, that.profiles) &&
                Objects.equals(exclusions, that.exclusions) &&
                Objects.equals(scalaVersion, that.scalaVersion) &&
                Objects.equals(keepProvidedDependencies, that.keepProvidedDependencies) &&
                Objects.equals(forceDepMgmtVersions, that.forceDepMgmtVersions) &&
                Objects.equals(enableDependencyOverrides, that.enableDependencyOverrides) &&
                Objects.equals(defaultConfiguration, that.defaultConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maxIterations,
                forceVersions,
                forcedProperties,
                profiles,
                exclusions,
                useSystemOsInfo,
                useSystemJdkVersion,
                scalaVersion,
                keepProvidedDependencies,
                forceDepMgmtVersions,
                enableDependencyOverrides,
                defaultConfiguration);
    }

    @Override
    public String toString() {
        return "ResolutionParams{" +
                "maxIterations=" + maxIterations +
                ", forceVersions=" + forceVersions +
                ", forcedProperties=" + forcedProperties +
                ", profiles=" + profiles +
                ", exclusions=" + exclusions +
                ", useSystemOsInfo=" + useSystemOsInfo +
                ", useSystemJdkVersion=" + useSystemJdkVersion +
                ", scalaVersion='" + scalaVersion + '\'' +
                ", keepProvidedDependencies=" + keepProvidedDependencies +
                ", forceDepMgmtVersions=" + forceDepMgmtVersions +
                ", enableDependencyOverrides=" + enableDependencyOverrides +
                ", defaultConfiguration=" + defaultConfiguration +
                '}';
    }

    public static ResolutionParams create() {
        return new ResolutionParams();
    }

    public static ResolutionParams of(ResolutionParams params) {
        return new ResolutionParams()
                .withMaxIterations(params.maxIterations)
                .withForceProperties(params.forcedProperties)
                .withForceVersions(params.forceVersions)
                .withProfiles(params.profiles)
                .withExclusions(params.exclusions)
                .withUseSystemOsInfo(params.useSystemOsInfo)
                .withUseSystemJdkVersion(params.useSystemJdkVersion)
                .withScalaVersion(params.scalaVersion)
                .withKeepProvidedDependencies(params.keepProvidedDependencies)
                .withForceDepMgmtVersions(params.forceDepMgmtVersions)
                .withEnableDependencyOverrides(params.enableDependencyOverrides)
                .withDefaultConfiguration(params.defaultConfiguration);
    }

    public ResolutionParams withMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public ResolutionParams forceProperty(String name, String value) {
        this.forcedProperties.put(name, value);
        return this;
    }

    public ResolutionParams forceProperties(Map<String, String> properties) {
        this.forcedProperties.putAll(properties);
        return this;
    }

    public ResolutionParams withForceProperties(Map<String, String> properties) {
        this.forcedProperties.clear();
        this.forcedProperties.putAll(properties);
        return this;
    }

    public ResolutionParams forceVersion(Module module, String version) {
        this.forceVersions.put(module, version);
        return this;
    }

    public ResolutionParams forceVersions(Map<Module, String> versions) {
        this.forceVersions.putAll(versions);
        return this;
    }

    public ResolutionParams withForceVersions(Map<Module, String> versions) {
        this.forceVersions.clear();
        this.forceVersions.putAll(versions);
        return this;
    }

    public ResolutionParams addProfile(String profile) {
        this.profiles.add(profile);
        return this;
    }
    public ResolutionParams removeProfile(String profile) {
        this.profiles.remove(profile);
        return this;
    }
    public ResolutionParams withProfiles(Set<String> profiles) {
        this.profiles.clear();
        this.profiles.addAll(profiles);
        return this;
    }

    public ResolutionParams addExclusion(String organization, String moduleName) {
        // FIXME Make the Map.Entry read-only?
        this.exclusions.add(new AbstractMap.SimpleEntry<>(organization, moduleName));
        return this;
    }
    public ResolutionParams removeExclusion(String organization, String moduleName) {
        this.exclusions.remove(new AbstractMap.SimpleEntry<>(organization, moduleName));
        return this;
    }
    public ResolutionParams withExclusions(Set<Map.Entry<String, String>> exclusions) {
        this.exclusions.clear();
        this.exclusions.addAll(exclusions);
        return this;
    }

    public ResolutionParams withUseSystemOsInfo(boolean useSystemOsInfo) {
        this.useSystemOsInfo = useSystemOsInfo;
        return this;
    }

    public ResolutionParams withUseSystemJdkVersion(boolean useSystemJdkVersion) {
        this.useSystemJdkVersion = useSystemJdkVersion;
        return this;
    }

    public ResolutionParams withScalaVersion(String scalaVersion) {
        this.scalaVersion = scalaVersion;
        return this;
    }

    public ResolutionParams withKeepProvidedDependencies(Boolean keepProvidedDependencies) {
        this.keepProvidedDependencies = keepProvidedDependencies;
        return this;
    }

    public ResolutionParams withForceDepMgmtVersions(Boolean forceDepMgmtVersions) {
        this.forceDepMgmtVersions = forceDepMgmtVersions;
        return this;
    }

    public ResolutionParams withEnableDependencyOverrides(Boolean enableDependencyOverrides) {
        this.enableDependencyOverrides = enableDependencyOverrides;
        return this;
    }

    public ResolutionParams withDefaultConfiguration(String defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
        return this;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public Map<String, String> getForcedProperties() {
        return Collections.unmodifiableMap(forcedProperties);
    }

    public Map<Module, String> getForceVersions() {
        return Collections.unmodifiableMap(forceVersions);
    }

    public Set<String> getProfiles() {
        return Collections.unmodifiableSet(profiles);
    }

    public Set<Map.Entry<String, String>> getExclusions() {
        return Collections.unmodifiableSet(exclusions);
    }

    public boolean getUseSystemOsInfo() {
        return useSystemOsInfo;
    }

    public boolean getUseSystemJdkVersion() {
        return useSystemJdkVersion;
    }

    public String getScalaVersion() {
        return scalaVersion;
    }

    public Boolean getKeepProvidedDependencies() {
        return keepProvidedDependencies;
    }

    public Boolean getForceDepMgmtVersions() {
        return forceDepMgmtVersions;
    }

    public Boolean getEnableDependencyOverrides() {
        return enableDependencyOverrides;
    }

    public String getDefaultConfiguration() {
        return defaultConfiguration;
    }
}
