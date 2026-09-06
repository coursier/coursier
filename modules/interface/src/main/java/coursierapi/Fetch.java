package coursierapi;

import coursier.internal.api.ApiHelper;
import coursierapi.error.CoursierError;

import java.io.File;
import java.util.*;

public final class Fetch {

    private final List<Dependency> dependencies;
    private final List<Dependency> bomDependencies;
    private final List<Repository> repositories;
    private Cache cache;
    private Boolean mainArtifacts;
    private final Set<String> classifiers;
    private Set<String> artifactTypes;
    private File fetchCache;
    private ResolutionParams resolutionParams;

    private Fetch() {
        dependencies = new ArrayList<>();
        bomDependencies = new ArrayList<>();
        repositories = new ArrayList<>(Arrays.asList(ApiHelper.defaultRepositories()));
        cache = Cache.create();
        mainArtifacts = null;
        classifiers = new HashSet<>();
        artifactTypes = null;
        fetchCache = null;
        resolutionParams = ResolutionParams.create();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fetch)) return false;
        Fetch fetch = (Fetch) o;
        return Objects.equals(dependencies, fetch.dependencies) &&
                Objects.equals(bomDependencies, fetch.bomDependencies) &&
                Objects.equals(repositories, fetch.repositories) &&
                Objects.equals(cache, fetch.cache) &&
                Objects.equals(mainArtifacts, fetch.mainArtifacts) &&
                Objects.equals(classifiers, fetch.classifiers) &&
                Objects.equals(artifactTypes, fetch.artifactTypes) &&
                Objects.equals(fetchCache, fetch.fetchCache) &&
                Objects.equals(resolutionParams, fetch.resolutionParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                dependencies,
                bomDependencies,
                repositories,
                cache,
                mainArtifacts,
                classifiers,
                artifactTypes,
                fetchCache,
                resolutionParams);
    }

    @Override
    public String toString() {
        return "Fetch{" +
                "dependencies=" + dependencies +
                ", bomDependencies=" + bomDependencies +
                ", repositories=" + repositories +
                ", cache=" + cache +
                ", mainArtifacts=" + mainArtifacts +
                ", classifiers=" + classifiers +
                ", artifactTypes=" + artifactTypes +
                ", fetchCache=" + fetchCache +
                ", resolutionParams=" + resolutionParams +
                '}';
    }

    public static Fetch create() {
        return new Fetch();
    }


    public Fetch addDependencies(Dependency... dependencies) {
        this.dependencies.addAll(Arrays.asList(dependencies));
        return this;
    }

    public Fetch withDependencies(Dependency... dependencies) {
        this.dependencies.clear();
        this.dependencies.addAll(Arrays.asList(dependencies));
        return this;
    }

    public Fetch addBomDependencies(Dependency... bomDependencies) {
        this.bomDependencies.addAll(Arrays.asList(bomDependencies));
        return this;
    }

    public Fetch withBomDependencies(Dependency... bomDependencies) {
        this.bomDependencies.clear();
        this.bomDependencies.addAll(Arrays.asList(bomDependencies));
        return this;
    }

    public Fetch addRepositories(Repository... repositories) {
        this.repositories.addAll(Arrays.asList(repositories));
        return this;
    }

    public Fetch withRepositories(Repository... repositories) {
        this.repositories.clear();
        this.repositories.addAll(Arrays.asList(repositories));
        return this;
    }

    public Fetch withCache(Cache cache) {
        this.cache = cache;
        return this;
    }

    public Fetch addCredentials(Credentials... credentials) {
        this.cache = this.cache.addCredentials(credentials);
        return this;
    }

    public Fetch addFileCredentials(String path) {
        this.cache = this.cache.addFileCredentials(path);
        return this;
    }

    public Fetch withMainArtifacts(Boolean mainArtifacts) {
        this.mainArtifacts = mainArtifacts;
        return this;
    }
    public Fetch withMainArtifacts(boolean mainArtifacts) {
        this.mainArtifacts = mainArtifacts;
        return this;
    }
    public Fetch withMainArtifacts() {
        this.mainArtifacts = true;
        return this;
    }

    public Fetch withClassifiers(Set<String> classifiers) {
        this.classifiers.clear();
        this.classifiers.addAll(classifiers);
        return this;
    }
    public Fetch addClassifiers(String... classifiers) {
        this.classifiers.addAll(Arrays.asList(classifiers));
        return this;
    }

    public Fetch withArtifactTypes(Set<String> types) {
        if (types == null) {
            this.artifactTypes = null;
        } else {
            this.artifactTypes = new HashSet<>(types);
        }
        return this;
    }
    public Fetch addArtifactTypes(String... types) {
        if (this.artifactTypes == null)
            this.artifactTypes = new HashSet<>();
        this.artifactTypes.addAll(Arrays.asList(types));
        return this;
    }

    /**
     * @deprecated Ignored. See {@link #withFetchCacheIKnowWhatImDoing(File)} if you
     * really want to rely on a fetch result cache, but beware its limitations.
     */
    @Deprecated
    public Fetch withFetchCache(File fetchCache) {
        // ignored
        return this;
    }

    /**
     * Cache the list of artifacts itself.
     *
     * Beware that if the fetch cache is read, the returned
     * `Artifact`s are empty, only the `File`s are valid.
     */
    public Fetch withFetchCacheIKnowWhatImDoing(File fetchCache) {
        this.fetchCache = fetchCache;
        return this;
    }

    public Fetch withResolutionParams(ResolutionParams resolutionParams) {
        this.resolutionParams = ResolutionParams.of(resolutionParams);
        return this;
    }

    public List<Dependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public List<Dependency> getBomDependencies() {
        return Collections.unmodifiableList(bomDependencies);
    }

    public List<Repository> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    public Cache getCache() {
        return cache;
    }

    public Boolean getMainArtifacts() {
        return mainArtifacts;
    }

    public Set<String> getClassifiers() {
        return Collections.unmodifiableSet(classifiers);
    }

    public Set<String> getArtifactTypes() {
        if (artifactTypes == null)
            return null;
        else
          return Collections.unmodifiableSet(artifactTypes);
    }

    /**
     * @deprecated See {@link #getFetchCacheIKnowWhatImDoing()}, and
     * {@link #withFetchCacheIKnowWhatImDoing(File)}.
     */
    @Deprecated
    public File getFetchCache() {
        return null;
    }

    public File getFetchCacheIKnowWhatImDoing() {
        return fetchCache;
    }

    public ResolutionParams getResolutionParams() {
        return resolutionParams;
    }

    public List<File> fetch() throws CoursierError {
        FetchResult result = fetchResult();
        return result.getFiles();
    }

    public FetchResult fetchResult() throws CoursierError {
        return ApiHelper.doFetch(this);
    }

}
