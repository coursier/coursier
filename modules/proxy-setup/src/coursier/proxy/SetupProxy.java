package coursier.proxy;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.*;

public final class SetupProxy {

    public static void setupTunnelingProp() {
        if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null)
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
    }

    private final static boolean debug;
    static {
        debug = Boolean.getBoolean("cs.proxy-setup.debug");
    }

    private final static class Handler extends DefaultHandler {

        private int settingsDistance = -1;
        private int proxiesDistance = -1;
        private int proxyDistance = -1;
        private String proxyKey = "";
        private String proxyValue = "";

        private ArrayList<Map<String, String>> proxies = new ArrayList<>();
        private HashMap<String, String> current = new HashMap<>();

        private final static String SETTINGS = "settings";
        private final static String PROXIES = "proxies";
        private final static String PROXY = "proxy";

        @Override
        public void startDocument() {
            settingsDistance = -1;
            proxiesDistance = -1;
            proxyDistance = -1;
            proxyKey = "";
            proxyValue = "";
            proxies = new ArrayList<>();
            current = new HashMap<>();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (settingsDistance < 0) {
                assert proxiesDistance < 0;
                assert proxyDistance < 0;
                if (qName.equals(SETTINGS))
                    settingsDistance = 0;
            } else if (settingsDistance == 0 && proxiesDistance < 0) {
                assert proxyDistance < 0;
                if (qName.equals(PROXIES))
                    proxiesDistance = 0;
                settingsDistance += 1;
            } else if (proxiesDistance == 0 && proxyDistance < 0) {
                assert settingsDistance >= 0;
                if (qName.equals(PROXY))
                    proxyDistance = 0;
                proxiesDistance += 1;
                settingsDistance += 1;
            } else {
                if (proxyDistance == 0) {
                    proxyKey = qName;
                    proxyValue = "";
                }

                if (proxyDistance >= 0)
                    proxyDistance += 1;
                if (proxiesDistance >= 0)
                    proxiesDistance += 1;
                settingsDistance += 1;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (proxyDistance == 1) {
                proxyValue = proxyValue + new String(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {

            if (proxyDistance == 1) {
                current.put(proxyKey, proxyValue);
                proxyKey = "";
                proxyValue = "";
            }

            if (proxiesDistance == 1) {
                proxies.add(Collections.unmodifiableMap(new HashMap<>(current)));
                current = new HashMap<>();
            }

            if (proxyDistance == 0 && qName.equals(PROXY))
                proxyDistance = -1;
            else if (proxyDistance > 0)
                proxyDistance -= 1;

            if (proxiesDistance == 0 && qName.equals(PROXIES))
                proxiesDistance = -1;
            else if (proxiesDistance > 0)
                proxiesDistance -= 1;

            if (settingsDistance == 0 && qName.equals(SETTINGS))
                settingsDistance = -1;
            else if (settingsDistance > 0)
                settingsDistance -= 1;
        }

        public List<Map<String, String>> getProxies() {
            return Collections.unmodifiableList(new ArrayList<>(proxies));
        }
    }

    private static void setProperty(String key, String value) {
        if (debug)
          System.err.println("cs-proxy: setProperty(" + key + ", " + value + ")");
        System.setProperty(key, value);
    }

    public static void setupPropertiesFrom(File m2Settings, String propertyPrefix) throws ParserConfigurationException, SAXException, IOException {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        Handler handler = new Handler();

        saxParser.parse(m2Settings, handler);

        List<Map<String, String>> proxies = handler.getProxies();
        for (Map<String, String> map : proxies) {
            String activeValue = map.get("active");
            boolean isActive = activeValue == null || activeValue.equalsIgnoreCase("true");

            if (debug) {
                System.err.println("cs-proxy, in map:");
                for (Map.Entry<String, String> e : map.entrySet()) {
                    System.err.println("  " + e.getKey() + "=" + e.getValue());
                }
            }

            if (isActive) {
                String protocolValue = map.get("protocol");
                String hostValue = map.get("host");
                String portValue = map.get("port");
                String usernameValue = map.get("username");
                String passwordValue = map.get("password");
                String nonProxyHostsValue = map.get("nonProxyHosts");

                if (hostValue != null) {
                    setupTunnelingProp();

                    String protocol = protocolValue == null ? "https" : protocolValue;

                    // Setting these is coursier-specific I think.
                    // These ensure that we send credentials only if the proxy itself uses that protocol
                    // (so that we don't send credentials to an HTTP proxy if it's supposed to use HTTPS).
                    setProperty(propertyPrefix + "http.proxyProtocol", protocol);
                    setProperty(propertyPrefix + "https.proxyProtocol", protocol);

                    setProperty(propertyPrefix + "http.proxyHost", hostValue);
                    setProperty(propertyPrefix + "https.proxyHost", hostValue);
                    if (portValue != null) {
                        setProperty(propertyPrefix + "http.proxyPort", portValue);
                        setProperty(propertyPrefix + "https.proxyPort", portValue);
                    }
                    if (usernameValue != null) {
                        setProperty(propertyPrefix + "http.proxyUser", usernameValue);
                        setProperty(propertyPrefix + "https.proxyUser", usernameValue);
                    }
                    if (passwordValue != null) {
                        setProperty(propertyPrefix + "http.proxyPassword", passwordValue);
                        setProperty(propertyPrefix + "https.proxyPassword", passwordValue);
                    }
                    if (nonProxyHostsValue != null)
                        // protocol is always http for this one
                        setProperty(propertyPrefix + "http.nonProxyHosts", nonProxyHostsValue);
                }
            }
        }
    }

    public static void setupProperties() throws ParserConfigurationException, SAXException, IOException {
        File m2Home = null;
        String fromEnvCs = System.getenv("CS_MAVEN_HOME");
        if (fromEnvCs != null)
            m2Home = new File(fromEnvCs);
        if (m2Home == null) {
          String fromEnv = System.getenv("MAVEN_HOME");
          if (fromEnv != null)
            m2Home = new File(fromEnv);
        }
        if (m2Home == null) {
          String fromProp = System.getProperty("cs.maven.home");
          if (fromProp != null)
            m2Home = new File(fromProp);
        }
        if (m2Home == null) {
          String fromProp = System.getProperty("maven.home");
          if (fromProp != null)
            m2Home = new File(fromProp);
        }
        if (m2Home == null)
          m2Home = new File(new File(System.getProperty("user.home")), ".m2");

        File m2Settings = new File(m2Home, "settings.xml");
        if (!m2Settings.isFile()) {
            if (debug)
                System.err.println("cs-proxy, not found:" + m2Settings);
            return;
        }
        if (debug)
            System.err.println("cs-proxy, found " + m2Settings);
        setupPropertiesFrom(m2Settings, "");
    }

    public static void setupAuthenticator(
            String httpProtocol,
            String httpHost,
            String httpPort,
            String httpUser,
            String httpPassword,
            String httpsProtocol,
            String httpsHost,
            String httpsPort,
            String httpsUser,
            String httpsPassword,
            String extraHttpProtocol,
            String extraHttpHost,
            String extraHttpPort,
            String extraHttpUser,
            String extraHttpPassword
    ) {

        boolean enable = httpHost != null && httpUser != null || httpsHost != null && httpsUser != null;

        if (!enable)
            return;

        setupTunnelingProp();
        Authenticator.setDefault(
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        String protocol = getRequestingProtocol();
                        String host = getRequestingHost();
                        String port = Integer.toString(getRequestingPort());
                        if (debug) {
                            System.err.println("cs-proxy, Authenticator.getPasswordAuthentication:");
                            System.err.println("  protocol=" + protocol);
                            System.err.println("  host=" + host);
                            System.err.println("  port=" + port);
                        }
                        if (protocol.equals(httpProtocol) && httpUser != null && host != null && host.equals(httpHost) && port.equals(httpPort))
                            return new PasswordAuthentication(httpUser, (httpPassword == null ? "" : httpPassword).toCharArray());
                        else if (protocol.equals(httpsProtocol) && httpsUser != null && host != null && host.equals(httpsHost) && port.equals(httpsPort))
                            return new PasswordAuthentication(httpsUser, (httpsPassword == null ? "" : httpsPassword).toCharArray());
                        else
                            return super.getPasswordAuthentication();
                    }
                }
        );
    }

    public static void setupAuthenticator() {

        String httpProtocol = System.getProperty("http.proxyProtocol");
        String httpHost = System.getProperty("http.proxyHost");
        String httpPortValue = System.getProperty("http.proxyPort");
        String httpUser = System.getProperty("http.proxyUser");
        String httpPassword = System.getProperty("http.proxyPassword");

        String httpPort = httpPortValue == null ? "80" : httpPortValue;

        String httpsProtocol = System.getProperty("https.proxyProtocol");
        String httpsHost = System.getProperty("https.proxyHost");
        String httpsPortValue = System.getProperty("https.proxyPort");
        String httpsUser = System.getProperty("https.proxyUser");
        String httpsPassword = System.getProperty("https.proxyPassword");

        String httpsPort = httpsPortValue == null ? "443" : httpsPortValue;

        setupAuthenticator(
                httpProtocol, httpHost, httpPort, httpUser, httpPassword,
                httpsProtocol, httpsHost, httpsPort, httpsUser, httpsPassword,
                null, null, null, null, null);
    }

    public static void setup() throws ParserConfigurationException, SAXException, IOException {
        setupProperties();
        setupAuthenticator();
    }
}
