package coursierapi;

import coursier.internal.api.ApiHelper;

public final class ProxySetup {

    private ProxySetup() {}

    /**
     * Sets up the JVM-wide proxy settings, from the coursier configuration.
     *
     * <p>This reads the proxy settings from the environment or from the coursier / Scala CLI
     * configuration file, and sets the corresponding JVM system properties and default
     * {@link java.net.Authenticator} accordingly.
     */
    public static void setup() {
        ApiHelper.proxySetup();
    }
}
