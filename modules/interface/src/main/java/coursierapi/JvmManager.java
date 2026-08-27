package coursierapi;

import coursier.internal.api.ApiHelper;

import java.io.File;
import java.util.Objects;

public final class JvmManager {

    private ArchiveCache archiveCache;
    private boolean update;

    private JvmManager() {
        this.archiveCache = ArchiveCache.create();
        this.update = false;
    }

    public static JvmManager create() {
        return new JvmManager();
    }

    public File get(String jvmId) {
        return ApiHelper.jvmManagerGet(this, jvmId);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JvmManager) {
            JvmManager other = (JvmManager) obj;
            return this.archiveCache.equals(other.archiveCache) &&
                    this.update == other.update;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 37 * (17 + archiveCache.hashCode()) + Boolean.hashCode(update);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("JvmManager(archiveCache=");
        b.append(archiveCache.toString());
        b.append(", update=");
        b.append(update);
        b.append(")");
        return b.toString();
    }

    public JvmManager setArchiveCache(ArchiveCache archiveCache) {
        this.archiveCache = archiveCache;
        return this;
    }

    public JvmManager setUpdate(boolean update) {
        this.update = update;
        return this;
    }

    public ArchiveCache getArchiveCache() {
        return archiveCache;
    }

    public boolean getUpdate() {
        return update;
    }

}
