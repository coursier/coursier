
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  private object versions {
    def argonautShapeless = "1.2.0-M11"
    def fastParse = setting {
      val sv = scalaVersion.value
      if (sv.startsWith("2.11.")) "2.1.2"
      else "2.1.3"
    }
    def http4s = "0.18.17"
    def okhttp = "3.14.3"
    def monadless = "0.0.13"
    def scalaz = "7.2.28"
  }

  def argonautShapeless = "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % versions.argonautShapeless
  def caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.0-M9"
  def catsCore = "org.typelevel" %% "cats-core" % "1.6.0"
  def dockerClient = "com.spotify" % "docker-client" % "8.16.0"
  def emoji = "com.lightbend" %% "emoji" % "1.2.1"
  def fastParse = setting {
    "com.lihaoyi" %% "fastparse" % versions.fastParse.value
  }
  def http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % versions.http4s
  def http4sDsl = "org.http4s" %% "http4s-dsl" % versions.http4s
  def jansi = "org.fusesource.jansi" % "jansi" % "1.18"
  def jlineTerminalJansi = "org.jline" % "jline-terminal-jansi" % "3.12.1"
  def jsoup = "org.jsoup" % "jsoup" % "1.12.1"
  def junit = "junit" % "junit" % "4.12"
  def logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  def mavenModel = "org.apache.maven" % "maven-model" % "3.6.2"
  // staying before 3.14.0 to the URLStreamHandlerFactory implementation
  def okhttp = "com.squareup.okhttp3" % "okhttp" % versions.okhttp
  def okhttpUrlConnection = "com.squareup.okhttp3" % "okhttp-urlconnection" % versions.okhttp
  def slf4JNop = "org.slf4j" % "slf4j-nop" % "1.7.28"
  def monadlessCats = "io.monadless" %% "monadless-cats" % versions.monadless
  def monadlessStdlib = "io.monadless" %% "monadless-stdlib" % versions.monadless

  def scalaAsync = Def.setting {
    val sv = scalaVersion.value
    val ver =
      if (sv.startsWith("2.11.")) "0.9.7"
      else "0.10.0"
    "org.scala-lang.modules" %% "scala-async" % ver
  }
  def scalaNativeTools03 = "org.scala-native" %% "tools" % "0.3.9"
  def scalaNativeTools040M2 = "org.scala-native" %% "tools" % "0.4.0-M2"
  def scalaReflect = setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  def scalatest = "org.scalatest" %% "scalatest" % "3.0.8"
  def scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  def scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % versions.scalaz

  object cross {
    // The setting / .value hoop-and-loop is necessary because of the expansion of the %%% macro, which references
    // other settings.

    def argonautShapeless = setting("com.github.alexarchambault" %%% "argonaut-shapeless_6.2" % versions.argonautShapeless)
    def catsEffect = setting("org.typelevel" %%% "cats-effect" % "1.2.0")
    def fastParse = setting {
      "com.lihaoyi" %%% "fastparse" % versions.fastParse.value
    }
    def scalaJsDom = setting("org.scala-js" %%% "scalajs-dom" % "0.9.7")
    def scalaJsJquery = setting("be.doeraene" %%% "scalajs-jquery" % "0.9.5")
    def scalaJsReact = setting("com.github.japgolly.scalajs-react" %%% "core" % "1.3.1")
    def scalazCore = setting("org.scalaz" %%% "scalaz-core" % versions.scalaz)
    def utest = setting {
      val sv = scalaVersion.value
      val ver =
        if (sv.startsWith("2.11.")) "0.6.7"
        else "0.6.9"
      "com.lihaoyi" %%% "utest" % ver
    }
  }

  def proguardVersion = "6.1.1"

}
