package coursier.internal.api;

import coursierapi.Logger;
import coursier.cache.CacheLogger;

final class WrappedLogger implements Logger {
    private final CacheLogger logger;
    private WrappedLogger(CacheLogger logger) {
        this.logger = logger;
    }

    public static WrappedLogger of(CacheLogger logger) {
        return new WrappedLogger(logger);
    }

    public CacheLogger getLogger() {
        return logger;
    }
}
