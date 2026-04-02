---
title: Proxy
---

By default, coursier relies on [`java.net.HttpURLConnection`](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html)
to handle HTTP requests. `java.net.HttpURLConnection` automatically picks up
[proxy related properties](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html).

## Properties

The following Java system properties control proxy behaviour:

| Property | Description |
|---|---|
| `https.proxyHost` | Hostname of the proxy for HTTPS connections |
| `https.proxyPort` | Port of the proxy for HTTPS connections (default: `443`) |
| `https.proxyUser` | Username for proxy authentication (HTTPS) |
| `https.proxyPassword` | Password for proxy authentication (HTTPS) |
| `http.proxyHost` | Hostname of the proxy for HTTP connections |
| `http.proxyPort` | Port of the proxy for HTTP connections (default: `80`) |
| `http.proxyUser` | Username for proxy authentication (HTTP) |
| `http.proxyPassword` | Password for proxy authentication (HTTP) |
| `http.nonProxyHosts` | Pipe-separated list of host patterns that bypass the proxy (e.g. `localhost|*.internal.example.com`) |

## Maven settings.xml

Coursier automatically reads proxy settings from the Maven `settings.xml` file.
It looks for `settings.xml` in the following locations, in order:

1. The directory given by the `CS_MAVEN_HOME` environment variable
2. The directory given by the `MAVEN_HOME` environment variable
3. The directory given by the `cs.maven.home` Java property
4. The directory given by the `maven.home` Java property
5. `~/.m2` (the Maven default)

If the file is found, coursier reads any `<proxy>` entries whose `<active>`
element is `true` (or absent) and configures the corresponding Java system
properties automatically.

Example `~/.m2/settings.xml`:
```xml
<settings>
  <proxies>
    <proxy>
      <id>my-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>proxy.example.com</host>
      <port>3128</port>
      <username>proxyuser</username>
      <password>proxypass</password>
      <nonProxyHosts>localhost|*.internal.example.com</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
```

The `<protocol>` element specifies whether the proxy itself communicates over
HTTP or HTTPS (it does **not** filter which target URLs go through the proxy).
If `<protocol>` is omitted, `https` is assumed.

## CLI

To set proxy properties directly, run the CLI with `-D` flags:
```
$ java -Dhttps.proxyHost=… -Dhttps.proxyPort=… \
     -jar $(which coursier) [args]
```
instead of `coursier [args]`. Alternatively, recent versions of coursier
should accept those options prefixed with `-J`, like
```
$ coursier -J-Dhttps.proxyHost=… -J-Dhttps.proxyPort=… [args]
```

## sbt

Set those properties when starting sbt, like
```
$ sbt -Dhttps.proxyHost=… -Dhttps.proxyPort=… [args]
```

Alternatively, pass those by [setting `JAVA_OPTS` in the environment](https://stackoverflow.com/questions/13803459/how-to-use-sbt-from-behind-proxy).

## API

Ensure the right Java properties are set when running your application, either
from the command line when running your application, via
```
$ java -Dhttps.proxyHost=… -Dhttps.proxyPort=… [args]
```
or by setting them yourself before anything coursier is run, like
```
System.setProperty("https.proxyHost", "…")
System.setProperty("https.proxyPort", "…")
```

If you want coursier to read proxy settings from `~/.m2/settings.xml`
automatically (the same way the CLI does), call `SetupProxy.setup()` early in
your application:
```java
import coursier.proxy.SetupProxy;

SetupProxy.setup(); // reads ~/.m2/settings.xml and registers a proxy Authenticator
```

`SetupProxy.setup()` both configures the proxy system properties
(`https.proxyHost`, etc.) from the Maven settings file and registers a
[`java.net.Authenticator`](https://docs.oracle.com/javase/8/docs/api/java/net/Authenticator.html)
so that authenticated proxies work correctly.
