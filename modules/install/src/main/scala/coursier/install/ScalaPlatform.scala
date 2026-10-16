package coursier.install

import coursier.cache.Cache
import coursier.core.Repository
import coursier.util.StringInterpolators._
import coursier.util.Task

abstract class ScalaPlatform extends Product with Serializable {
  def availableVersions(cache: Cache[Task], repositories: Seq[Repository]): Set[String]
  def suffix(ver: String): String

  /** Name of this platform, for error messages */
  private[install] def description: String = toString

  /** Like [[availableVersions]], but returns the errors of each repository if none of them could
    * list versions
    */
  private[install] def listAvailableVersions(
    cache: Cache[Task],
    repositories: Seq[Repository]
  ): Either[Seq[String], Set[String]] =
    Right(availableVersions(cache, repositories))
}

object ScalaPlatform {

  private def binaryVersion(v: String): String =
    if (v.forall(c => c.isDigit || c == '.'))
      v.split('.').take(2).mkString(".")
    else
      v

  final case object Native extends ScalaPlatform {
    def availableVersions(cache: Cache[Task], repositories: Seq[Repository]): Set[String] =
      listAvailableVersions(cache, repositories).getOrElse(Set.empty)
    private[install] override def description: String =
      "Scala Native"
    private[install] override def listAvailableVersions(
      cache: Cache[Task],
      repositories: Seq[Repository]
    ): Either[Seq[String], Set[String]] =
      AppDescriptor.listVersions(cache, repositories, mod"org.scala-native:tools_2.12")
        .map(_.map(binaryVersion))
    def suffix(ver: String): String =
      s"_native$ver"
  }

  final case object JS extends ScalaPlatform {
    def availableVersions(cache: Cache[Task], repositories: Seq[Repository]): Set[String] =
      listAvailableVersions(cache, repositories).getOrElse(Set.empty)
    private[install] override def description: String =
      "Scala.js"
    private[install] override def listAvailableVersions(
      cache: Cache[Task],
      repositories: Seq[Repository]
    ): Either[Seq[String], Set[String]] =
      AppDescriptor.listVersions(cache, repositories, mod"org.scala-js:scalajs-tools_2.12")
        .map(_.map(binaryVersion))
    def suffix(ver: String): String =
      s"_sjs$ver"
  }

}
