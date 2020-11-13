
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._
import sbt.Def.setting
import sbt.Keys._

object Deps {

  private object versions {
    def argonautShapeless = "1.2.0"
    def collectionCompat = "2.2.0"
    def fastParse = "2.3.0"
    def http4s = "0.18.26"
    def jsoniterScala = "2.2.4"
    def monadless = "0.0.13"
    def scalaz = "7.2.30"
  }

  def argonautShapeless = "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % versions.argonautShapeless
  def caseApp = "com.github.alexarchambault" %% "case-app" % "2.0.0"
  def catsCore = "org.typelevel" %% "cats-core" % "2.2.0"
  def collectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % versions.collectionCompat
  def concurrentReferenceHashMap = "io.github.alexarchambault" % "concurrent-reference-hash-map" % "1.0.0"
  def dataClass = "io.github.alexarchambault" %% "data-class" % "0.2.4"
  def dockerClient = "com.spotify" % "docker-client" % "8.16.0"
  def fastParse = "com.lihaoyi" %% "fastparse" % versions.fastParse
  def http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % versions.http4s
  def http4sDsl = "org.http4s" %% "http4s-dsl" % versions.http4s
  def jimfs = "com.google.jimfs" % "jimfs" % "1.1"
  def jol = "org.openjdk.jol" % "jol-core" % "0.14"
  def jsoniterCore = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % versions.jsoniterScala
  def jsoniterMacros = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % versions.jsoniterScala
  def jsoup = "org.jsoup" % "jsoup" % "1.13.1"
  def logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  def mavenModel = "org.apache.maven" % "maven-model" % "3.6.3"
  def okhttp = "com.squareup.okhttp3" % "okhttp" % "3.13.1"
  def simulacrum = "org.typelevel" %% "simulacrum" % "1.0.0"
  def slf4JNop = "org.slf4j" % "slf4j-nop" % "1.7.30"
  def svm = "org.graalvm.nativeimage" % "svm" % "20.1.0"
  def svmSubs = "org.scalameta" %% "svm-subs" % "20.1.0"
  def monadlessCats = "io.monadless" %% "monadless-cats" % versions.monadless
  def monadlessStdlib = "io.monadless" %% "monadless-stdlib" % versions.monadless
  def plexusArchiver = "org.codehaus.plexus" % "plexus-archiver" % "4.2.2"
  def plexusContainerDefault = "org.codehaus.plexus" % "plexus-container-default" % "2.1.0" // plexus-archiver needs its loggers

  def scalaAsync = "org.scala-lang.modules" %% "scala-async" % "0.10.0"
  def scalaNativeTools03 = "org.scala-native" %% "tools" % "0.3.9"
  def scalaNativeTools040M2 = "org.scala-native" %% "tools" % "0.4.0-M2"
  def scalaReflect = setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  def scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
  def scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % versions.scalaz
  def windowsAnsi = "io.github.alexarchambault.windows-ansi" % "windows-ansi" % "0.0.3"

  object cross {
    // The setting / .value hoop-and-loop is necessary because of the expansion of the %%% macro, which references
    // other settings.

    def argonautShapeless = setting("com.github.alexarchambault" %%% "argonaut-shapeless_6.2" % versions.argonautShapeless)
    def catsEffect = setting("org.typelevel" %%% "cats-effect" % "2.1.4")
    def collectionCompat = setting("org.scala-lang.modules" %%% "scala-collection-compat" % versions.collectionCompat)
    def fastParse = setting("com.lihaoyi" %%% "fastparse" % versions.fastParse)
    def scalaJsDom = setting("org.scala-js" %%% "scalajs-dom" % "1.1.0")
    def scalaJsJquery = setting("be.doeraene" %%% "scalajs-jquery" % "1.0.0")
    def scalaJsReact = setting("com.github.japgolly.scalajs-react" %%% "core" % "1.7.0")
    def scalazCore = setting("org.scalaz" %%% "scalaz-core" % versions.scalaz)
    def utest = setting("com.lihaoyi" %%% "utest" % "0.7.5")
  }

  def proguardVersion = "7.0.0"

}
