package coursier

import coursier.ivy.IvyRepository

object Repositories {
  def central: MavenRepository =
    MavenRepository("https://repo1.maven.org/maven2")
  def sonatype(name: String): MavenRepository =
    MavenRepository(s"https://oss.sonatype.org/content/repositories/$name")
  def bintray(id: String): MavenRepository =
    MavenRepository(s"https://dl.bintray.com/$id")
  def bintray(owner: String, repo: String): MavenRepository =
    bintray(s"$owner/$repo")
  def bintrayIvy(id: String): IvyRepository =
    IvyRepository.fromPattern(
      s"https://dl.bintray.com/${id.stripSuffix("/")}/" +:
        coursier.ivy.Pattern.default
    )
  def typesafe(id: String): MavenRepository =
    MavenRepository(s"https://repo.typesafe.com/typesafe/$id")
  def typesafeIvy(id: String): IvyRepository =
    IvyRepository.fromPattern(
      s"https://repo.typesafe.com/typesafe/ivy-$id/" +:
        coursier.ivy.Pattern.default
    )
  def sbtPlugin(id: String): IvyRepository =
    IvyRepository.fromPattern(
      s"https://repo.scala-sbt.org/scalasbt/sbt-plugin-$id/" +:
        coursier.ivy.Pattern.default
    )
  def sbtMaven(id: String): MavenRepository =
    MavenRepository(s"https://repo.scala-sbt.org/scalasbt/maven-$id")
  def jitpack: MavenRepository =
    MavenRepository("https://jitpack.io")
}
