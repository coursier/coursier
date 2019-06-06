
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  def catsCore = "org.typelevel" %% "cats-core" % "1.6.0"
  def fastParse = "com.lihaoyi" %% "fastparse" % SharedVersions.fastParse
  def jsoup = "org.jsoup" % "jsoup" % "1.12.1"
  def scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  def scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % SharedVersions.scalaz
  def caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.0-M8"
  // staying before 3.14.0 to the URLStreamHandlerFactory implementation
  def okhttpUrlConnection = "com.squareup.okhttp3" % "okhttp-urlconnection" % "3.14.2"
  def argonautShapeless = "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % SharedVersions.argonautShapeless
  def scalatest = "org.scalatest" %% "scalatest" % "3.0.7"
  def junit = "junit" % "junit" % "4.12"
  def dockerClient = "com.spotify" % "docker-client" % "8.16.0"

  def scalaAsync = Def.setting {
    val sv = scalaVersion.value
    val ver =
      if (sv.startsWith("2.11.")) "0.9.7"
      else "0.10.0"
    "org.scala-lang.modules" %% "scala-async" % ver
  }

  def scalaNativeTools03 = "org.scala-native" %% "tools" % "0.3.9"
  def scalaNativeTools040M2 = "org.scala-native" %% "tools" % "0.4.0-M2"

  def slf4JNop = "org.slf4j" % "slf4j-nop" % "1.7.26"

  def scalaReflect = setting("org.scala-lang" % "scala-reflect" % scalaVersion.value) 
}
