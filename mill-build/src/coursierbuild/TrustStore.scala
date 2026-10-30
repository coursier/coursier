package coursierbuild

import java.security.KeyStore
import java.security.cert.X509Certificate

import scala.jdk.CollectionConverters.*

/** The trust store embedded in the native launchers
  *
  * native-image embeds the trust store it's given at build time in the images it generates. By
  * default, that's the one of the GraalVM it runs with, and the GraalVM we build the launchers with
  * ([[GraalVm]]) still trusts the roots of the compromised eMudhra intermediate CAs
  * (CVE-2026-91403, CVE-2026-114820, see https://emudhra.we-have-a-problem.com). So we pass it that
  * of a JDK that doesn't trust them anymore instead.
  *
  * This can be removed once [[GraalVm]] points to a GraalVM that has the fixes for this.
  *
  * Like [[GraalVm]], the archive the trust store comes from is pinned by URL and SHA-256 (the
  * `digest` of the GitHub release asset, whose SHA-1 also matches the one BellSoft publishes at
  * `https://api.bell-sw.com/v1/liberica/releases`). The trust store doesn't depend on the platform,
  * so the same archive is used on all of them.
  */
object TrustStore {

  private def archive = GraalVm.PinnedArchive(
    "https://github.com/bell-sw/Liberica/releases/download/25.0.5.0.3+5/bellsoft-jre25.0.5.0.3+5-linux-amd64.tar.gz",
    "78846aeed480e29ba3c987e61d3c7b437163f005b20e41bb2cdf7cf8ccbe76f3"
  )

  /** Common names of the roots the compromised eMudhra intermediate CAs chain to, that upstream
    * OpenJDK removed from its trust store
    */
  private def distrustedRoots = Set(
    "emSign Root CA - G1",
    "emSign Root CA - G2",
    "emSign ECC Root CA - G3",
    "AAA Certificate Services"
  )

  private def commonName(cert: X509Certificate): Option[String] =
    new javax.naming.ldap.LdapName(cert.getSubjectX500Principal.getName)
      .getRdns
      .asScala
      .find(_.getType.equalsIgnoreCase("CN"))
      .map(_.getValue.toString)

  /** Downloads the trust store, and checks that it doesn't trust the distrusted roots
    *
    * @return
    *   the path of the trust store
    */
  def cacerts(): os.Path = {
    val path     = archive.javaHome() / "lib/security/cacerts"
    val keyStore = KeyStore.getInstance(path.toIO, "changeit".toCharArray)
    val names    = keyStore.aliases().asScala.toVector.flatMap { alias =>
      keyStore.getCertificate(alias) match {
        case cert: X509Certificate => commonName(cert).toSeq
        case _                     => Nil
      }
    }
    // just in case we end up with an empty or truncated trust store
    if (names.length < 50)
      sys.error(s"Only found ${names.length} certificates in $path")
    val stillTrusted = names.filter(distrustedRoots).distinct
    if (stillTrusted.nonEmpty)
      sys.error(s"$path still trusts distrusted roots: ${stillTrusted.mkString(", ")}")
    path
  }
}
