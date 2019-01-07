---
title: Proxy
---

By default, coursier relies on [`java.net.HttpURLConnection`](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html)
to handle HTTP requests. `java.net.HttpURLConnection` automatically picks up
[proxy related properties](https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html). `https.proxyHost` and `https.proxyPort` typically need to be set.

## CLI

To set one of those properties, run the CLI like
```
$ java -jar $(which coursier) \
    -Dhttps.proxyHost=… -Dhttps.proxyPort=… [args]
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
