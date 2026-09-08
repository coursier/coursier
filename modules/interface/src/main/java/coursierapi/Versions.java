package coursierapi;

import coursier.internal.api.ApiHelper;
import coursierapi.error.CoursierError;

import java.util.*;

public final class Versions {

    private final List<Repository> repositories;
    private Cache cache;
    private Module module;

    private Versions() {
        repositories = new ArrayList<>(Arrays.asList(ApiHelper.defaultRepositories()));
        cache = Cache.create();
        module = null;
    }

    public static Versions create() {
        return new Versions();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Versions) {
            Versions other = (Versions) obj;
            return this.repositories.equals(other.repositories) &&
                    this.cache.equals(other.cache) &&
                    Objects.equals(this.module, other.module);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "coursierapi.Versions".hashCode()) + repositories.hashCode()) + cache.hashCode()) + Objects.hashCode(module);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Versions(repositories=[");
        for (Repository repo : repositories) {
            b.append(repo.toString());
            b.append(", ");
        }
        b.append("], cache=");
        b.append(cache.toString());
        b.append("], module=");
        b.append(module);
        b.append(")");
        return b.toString();
    }

    public Versions addRepositories(Repository... repositories) {
        this.repositories.addAll(Arrays.asList(repositories));
        return this;
    }

    public Versions withRepositories(Repository... repositories) {
        this.repositories.clear();
        this.repositories.addAll(Arrays.asList(repositories));
        return this;
    }

    public Versions withCache(Cache cache) {
        this.cache = cache;
        return this;
    }

    public Versions addCredentials(Credentials... credentials) {
        this.cache = this.cache.addCredentials(credentials);
        return this;
    }

    public Versions addFileCredentials(String path) {
        this.cache = this.cache.addFileCredentials(path);
        return this;
    }

    public Versions withModule(Module module) {
        this.module = module;
        return this;
    }

    public List<Repository> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    public Cache getCache() {
        return cache;
    }

    public Module getModule() {
        return module;
    }

    public VersionsResult versions() throws CoursierError {
        return ApiHelper.getVersions(this);
    }
}
