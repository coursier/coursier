package coursier.bootstrap.launcher.proxy;

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
            } else if (proxiesDistance == 0 && proxyDistance < 0) {
                if (qName.equals(PROXY))
                    proxyDistance = 0;
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

    public static void setupPropertiesFrom(File m2Settings, String propertyPrefix) throws ParserConfigurationException, SAXException, IOException {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        Handler handler = new Handler();

        saxParser.parse(m2Settings, handler);

        List<Map<String, String>> proxies = handler.getProxies();
        for (Map<String, String> map : proxies) {
            String activeValue = map.get("active");
            boolean isActive = activeValue == null || activeValue.equalsIgnoreCase("true");

            if (isActive) {
                String protocolValue = map.get("protocol");
                String hostValue = map.get("host");
                String portValue = map.get("port");
                String usernameValue = map.get("username");
                String passwordValue = map.get("password");
                String nonProxyHostsValue = map.get("nonProxyHosts");

                if (hostValue != null) {
                    setupTunnelingProp();

                    String protocol = protocolValue == null ? "" : protocolValue;
                    System.setProperty(propertyPrefix + protocol + ".proxyHost", hostValue);
                    if (portValue != null)
                        System.setProperty(propertyPrefix + protocol + ".proxyPort", portValue);
                    if (usernameValue != null)
                        System.setProperty(propertyPrefix + protocol + ".proxyUser", usernameValue);
                    if (passwordValue != null)
                        System.setProperty(propertyPrefix + protocol + ".proxyPassword", passwordValue);
                    if (nonProxyHostsValue != null)
                        // protocol is always http for this one
                        System.setProperty(propertyPrefix + "http.nonProxyHosts", nonProxyHostsValue);
                }
            }
        }
    }

    public static void setupProperties() throws ParserConfigurationException, SAXException, IOException {
        File m2Settings = new File(new File(System.getProperty("user.home")), ".m2/settings.xml");
        if (!m2Settings.isFile())
            return;
        setupPropertiesFrom(m2Settings, "");
    }

    public static void setupAuthenticator(
            String httpHost,
            String httpPort,
            String httpUser,
            String httpPassword,
            String httpsHost,
            String httpsPort,
            String httpsUser,
            String httpsPassword,
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
                        if ("http".equals(protocol) && httpUser != null && host != null && host.equals(httpHost) && port.equals(httpPort))
                            return new PasswordAuthentication(httpUser, (httpPassword == null ? "" : httpPassword).toCharArray());
                        else if ("https".equals(protocol) && httpsUser != null && host != null && host.equals(httpsHost) && port.equals(httpsPort))
                            return new PasswordAuthentication(httpsUser, (httpsPassword == null ? "" : httpsPassword).toCharArray());
                        else
                            return super.getPasswordAuthentication();
                    }
                }
        );
    }

    public static void setupAuthenticator() {

        String httpHost = System.getProperty("http.proxyHost");
        String httpPortValue = System.getProperty("http.proxyPort");
        String httpUser = System.getProperty("http.proxyUser");
        String httpPassword = System.getProperty("http.proxyPassword");

        String httpPort = httpPortValue == null ? "80" : httpPortValue;

        String httpsHost = System.getProperty("https.proxyHost");
        String httpsPortValue = System.getProperty("https.proxyPort");
        String httpsUser = System.getProperty("https.proxyUser");
        String httpsPassword = System.getProperty("https.proxyPassword");

        String httpsPort = httpsPortValue == null ? "443" : httpsPortValue;

        setupAuthenticator(
                httpHost, httpPort, httpUser, httpPassword,
                httpsHost, httpsPort, httpsUser, httpsPassword,
                null, null, null, null);
    }

    public static void setup() throws ParserConfigurationException, SAXException, IOException {
        setupProperties();
        setupAuthenticator();
    }
}
