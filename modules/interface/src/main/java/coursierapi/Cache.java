package coursierapi;

import coursier.internal.api.ApiHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public final class Cache {

    private ExecutorService pool;
    private File location;
    private Logger logger;
    private List<Credentials> credentials;
    private List<String> credentialFiles;

    private Cache() {
        pool = ApiHelper.defaultPool();
        location = ApiHelper.defaultLocation();
        logger = null;
        credentials = Collections.emptyList();
        credentialFiles = Collections.emptyList();
    }

    public static Cache create() {
        return new Cache();
    }

    public File get(Artifact artifact) {
        return ApiHelper.cacheGet(this, artifact);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Cache) {
            Cache other = (Cache) obj;
            return this.pool.equals(other.pool) &&
                    this.location.equals(other.location) &&
                    Objects.equals(this.logger, other.logger) &&
                    this.credentials.equals(other.credentials) &&
                    this.credentialFiles.equals(other.credentialFiles);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (17 + pool.hashCode()) + location.hashCode()) + Objects.hashCode(logger)) + credentials.hashCode()) + credentialFiles.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Cache(pool=");
        b.append(pool.toString());
        b.append(", location=");
        b.append(location.toString());
        if (logger != null) {
            b.append(", logger=");
            b.append(logger.toString());
        }
        if (!credentials.isEmpty()) {
            b.append(", credentials=");
            b.append(credentials.toString());
        }
        if (!credentialFiles.isEmpty()) {
            b.append(", credentialFiles=");
            b.append(credentialFiles.toString());
        }
        b.append(")");
        return b.toString();
    }

    public Cache withPool(ExecutorService pool) {
        this.pool = pool;
        return this;
    }

    public Cache withLocation(File location) {
        this.location = location;
        return this;
    }

    public Cache withLogger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public Cache withCredentials(List<Credentials> credentials) {
        this.credentials = new ArrayList<>(credentials);
        return this;
    }

    public Cache withCredentials(Credentials... credentials) {
        this.credentials = new ArrayList<>(Arrays.asList(credentials));
        return this;
    }

    public Cache addCredentials(Credentials... credentials) {
        ArrayList<Credentials> newCredentials = new ArrayList<>(this.credentials);
        newCredentials.addAll(Arrays.asList(credentials));
        this.credentials = newCredentials;
        return this;
    }

    public Cache addFileCredentials(String path) {
        ArrayList<String> newFiles = new ArrayList<>(this.credentialFiles);
        newFiles.add(path);
        this.credentialFiles = newFiles;
        return this;
    }

    public Cache addFileCredentials(File file) {
        return addFileCredentials(file.getAbsolutePath());
    }

    public ExecutorService getPool() {
        return pool;
    }

    public File getLocation() {
        return location;
    }

    public Logger getLogger() {
        return logger;
    }

    public List<Credentials> getCredentials() {
        return Collections.unmodifiableList(credentials);
    }

    public List<String> getCredentialFiles() {
        return Collections.unmodifiableList(credentialFiles);
    }
}
