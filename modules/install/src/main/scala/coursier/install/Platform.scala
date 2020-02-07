package coursier.install

import coursier.moduleString
import coursier.cache.Cache
import coursier.core.Repository
import coursier.util.Task

abstract class Platform extends Product with Serializable {
  def availableVersions(cache: Cache[Task], repositories: Seq[Repository]): Set[String]
  def suffix(ver: String): String
}

object Platform {

  private def binaryVersion(v: String): String =
    if (v.forall(c => c.isDigit || c == '.'))
      v.split('.').take(2).mkString(".")
    else
      v

  final case object Native extends Platform {
    def availableVersions(cache: Cache[Task], repositories: Seq[Repository]): Set[String] =
      AppArtifacts.listVersions(cache, repositories, mod"org.scala-native:tools_2.12")
        .map(binaryVersion)
    def suffix(ver: String): String =
      s"_native$ver"
  }

  final case object JS extends Platform {
    def availableVersions(cache: Cache[Task], repositories: Seq[Repository]): Set[String] =
      AppArtifacts.listVersions(cache, repositories, mod"org.scala-js:scalajs-tools_2.12")
        .map(binaryVersion)
    def suffix(ver: String): String =
      s"_sjs$ver"
  }

}
