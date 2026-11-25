package coursier.cache

/** The networking types the cache is written against.
  *
  * On the JVM these are plain aliases for the `java.net` / `javax.net.ssl` types, so signatures
  * mentioning them are unchanged - both at the source and at the bytecode level. Scala Native has
  * no `java.net.URL`, `URLConnection` nor TLS in its javalib, so it provides its own
  * implementations under the same names.
  */
package object net {

  type URL                   = java.net.URL
  type URLConnection         = java.net.URLConnection
  type HttpURLConnection     = java.net.HttpURLConnection
  type Proxy                 = java.net.Proxy
  type MalformedURLException = java.net.MalformedURLException

  type HttpsURLConnection = javax.net.ssl.HttpsURLConnection
  type SSLSocketFactory   = javax.net.ssl.SSLSocketFactory
  type HostnameVerifier   = javax.net.ssl.HostnameVerifier
  type SSLException       = javax.net.ssl.SSLException

}
