package coursierapi;

import coursier.internal.api.ApiHelper;
import coursierapi.error.CoursierError;

import java.util.*;

public final class Complete {

    private final List<Repository> repositories;
    private Cache cache;
    private String input;
    private String scalaVersion;
    private String scalaBinaryVersion;

    private Complete() {
        repositories = new ArrayList<>(Arrays.asList(ApiHelper.defaultRepositories()));
        cache = Cache.create();
        input = "";
        scalaVersion = null;
        scalaBinaryVersion = null;
    }

    public static Complete create() {
        return new Complete();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Complete) {
            Complete other = (Complete) obj;
            return this.repositories.equals(other.repositories) &&
                    this.cache.equals(other.cache) &&
                    this.input.equals(other.input) &&
                    Objects.equals(this.scalaVersion, other.scalaVersion) &&
                    Objects.equals(this.scalaBinaryVersion, other.scalaBinaryVersion);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "coursierapi.Complete".hashCode()) + repositories.hashCode()) + cache.hashCode()) + input.hashCode()) + Objects.hashCode(scalaVersion)) + Objects.hashCode(scalaBinaryVersion);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Complete(repositories=[");
        for (Repository repo : repositories) {
            b.append(repo.toString());
            b.append(", ");
        }
        b.append("], cache=");
        b.append(cache.toString());
        b.append("], input=");
        b.append(input);
        if (scalaVersion != null) {
            b.append("], scalaVersion=");
            b.append(scalaVersion);
        }
        if (scalaBinaryVersion != null) {
            b.append("], scalaBinaryVersion=");
            b.append(scalaBinaryVersion);
        }
        b.append(")");
        return b.toString();
    }

    public Complete addRepositories(Repository... repositories) {
        this.repositories.addAll(Arrays.asList(repositories));
        return this;
    }

    public Complete withRepositories(Repository... repositories) {
        this.repositories.clear();
        this.repositories.addAll(Arrays.asList(repositories));
        return this;
    }

    public Complete withCache(Cache cache) {
        this.cache = cache;
        return this;
    }

    public Complete addCredentials(Credentials... credentials) {
        this.cache = this.cache.addCredentials(credentials);
        return this;
    }

    public Complete addFileCredentials(String path) {
        this.cache = this.cache.addFileCredentials(path);
        return this;
    }

    public Complete withInput(String input) {
        this.input = input;
        return this;
    }

    public Complete withScalaVersion(String scalaVersion) {
        this.scalaVersion = scalaVersion;
        return this;
    }

    public Complete withScalaBinaryVersion(String scalaBinaryVersion) {
        this.scalaBinaryVersion = scalaBinaryVersion;
        return this;
    }

    public List<Repository> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    public Cache getCache() {
        return cache;
    }

    public String getInput() {
        return input;
    }

    public String getScalaVersion() {
        return scalaVersion;
    }

    public String getScalaBinaryVersion() {
        return scalaBinaryVersion;
    }

    public CompleteResult complete() throws CoursierError {
        return ApiHelper.doComplete(this);
    }
}
