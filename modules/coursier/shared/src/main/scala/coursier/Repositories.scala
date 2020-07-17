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
  def clojars: MavenRepository =
    MavenRepository("https://repo.clojars.org")
  def jcenter: MavenRepository =
    MavenRepository("https://jcenter.bintray.com")
  def google: MavenRepository =
    MavenRepository("https://maven.google.com")

  // https://storage-download.googleapis.com/maven-central/index.html
  def centralGcs: MavenRepository =
    MavenRepository("https://maven-central.storage-download.googleapis.com/maven2")
  def centralGcsEu: MavenRepository =
    MavenRepository("https://maven-central-eu.storage-download.googleapis.com/maven2")
  def centralGcsAsia: MavenRepository =
    MavenRepository("https://maven-central-asia.storage-download.googleapis.com/maven2")
}
