package coursierapi;

import coursier.internal.api.ApiHelper;

import java.io.File;

public final class ArchiveCache {

    private File location;
    private Cache cache;

    private ArchiveCache() {
        location = ApiHelper.defaultArchiveCacheLocation();
        cache = Cache.create();
    }

    public static ArchiveCache create() {
        return new ArchiveCache();
    }

    public File get(Artifact artifact) {
        return ApiHelper.archiveCacheGet(this, artifact);
    }

    public File getIfExists(Artifact artifact) {
        return ApiHelper.archiveCacheGetIfExists(this, artifact);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArchiveCache) {
            ArchiveCache other = (ArchiveCache) obj;
            return this.location.equals(other.location) &&
                    this.cache.equals(other.cache);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (17 + location.hashCode()) + cache.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("ArchiveCache(location=");
        b.append(location.toString());
        b.append(", cache=");
        b.append(cache.toString());
        b.append(")");
        return b.toString();
    }

    public ArchiveCache withLocation(File location) {
        this.location = location;
        return this;
    }

    public ArchiveCache withCache(Cache cache) {
        this.cache = cache;
        return this;
    }

    public File getLocation() {
        return location;
    }

    public Cache getCache() {
        return cache;
    }

}
