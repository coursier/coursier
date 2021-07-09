import mill._, scalalib._

object Deps {
  def argonautShapeless = ivy"com.github.alexarchambault::argonaut-shapeless_6.2::1.2.0"
  def caseApp          = ivy"com.github.alexarchambault::case-app:2.1.0-M3"
  def catsCore         = ivy"org.typelevel::cats-core:2.6.1"
  def catsEffect       = ivy"org.typelevel::cats-effect::2.5.1"
  def collectionCompat = ivy"org.scala-lang.modules::scala-collection-compat::2.2.0"
  def concurrentReferenceHashMap = ivy"io.github.alexarchambault:concurrent-reference-hash-map:1.1.0"
  def dataClass        = ivy"io.github.alexarchambault::data-class:0.2.5"
  def dockerClient     = ivy"com.spotify:docker-client:8.16.0"
  def fastParse        = ivy"com.lihaoyi::fastparse::${Versions.fastParse}"
  def http4sBlazeServer = ivy"org.http4s::http4s-blaze-server:${Versions.http4s}"
  def http4sDsl        = ivy"org.http4s::http4s-dsl:${Versions.http4s}"
  def java8Compat      = ivy"org.scala-lang.modules::scala-java8-compat:1.0.0"
  def jimfs            = ivy"com.google.jimfs:jimfs:1.2"
  def jniUtils         = ivy"io.get-coursier.jniutils:windows-jni-utils:${Versions.jniUtils}"
  def jniUtilsBootstrap = ivy"io.get-coursier.jniutils:windows-jni-utils-bootstrap:${Versions.jniUtils}"
  def jol              = ivy"org.openjdk.jol:jol-core:0.16"
  def jsoniterCore     = ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:${Versions.jsoniterScala}"
  def jsoniterMacros   = ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
  def jsoup            = ivy"org.jsoup:jsoup:1.13.1"
  def logbackClassic   = ivy"ch.qos.logback:logback-classic:1.2.3"
  def macroParadise    = ivy"org.scalamacros:::paradise:2.1.1"
  def mdoc             = ivy"org.scalameta::mdoc:2.2.21"
  def monadlessCats    = ivy"io.monadless::monadless-cats:${Versions.monadless}"
  def monadlessStdlib = ivy"io.monadless::monadless-stdlib:${Versions.monadless}"
  def okhttp           = ivy"com.squareup.okhttp3:okhttp:3.14.9"
  def plexusArchiver   = ivy"org.codehaus.plexus:plexus-archiver:4.2.5"
  def plexusContainerDefault = ivy"org.codehaus.plexus:plexus-container-default:2.1.0" // plexus-archiver needs its loggers
  def proguard         = ivy"com.guardsquare:proguard-base:7.1.0"
  def scalaAsync       = ivy"org.scala-lang.modules::scala-async:0.10.0"
  def scalaJsDom       = ivy"org.scala-js::scalajs-dom::1.1.0"
  def scalaJsJquery    = ivy"be.doeraene::scalajs-jquery::1.0.0"
  def scalaJsReact     = ivy"com.github.japgolly.scalajs-react::core::1.7.7"
  def scalaNativeTools03 = ivy"org.scala-native::tools:0.3.9"
  def scalaNativeTools040M2 = ivy"org.scala-native::tools:0.4.0-M2"
  def scalaNativeTools040 = ivy"org.scala-native::tools:0.4.0"
  def scalaReflect(sv: String) = ivy"org.scala-lang:scala-reflect:$sv"
  def scalaXml         = ivy"org.scala-lang.modules::scala-xml:2.0.0"
  def scalazCore       = ivy"org.scalaz::scalaz-core::${Versions.scalaz}"
  def scalazConcurrent = ivy"org.scalaz::scalaz-concurrent:${Versions.scalaz}"
  def simulacrum       = ivy"org.typelevel::simulacrum:1.0.0"
  def slf4JNop         = ivy"org.slf4j:slf4j-nop:1.7.31"
  def svm              = ivy"org.graalvm.nativeimage:svm:$graalVmVersion"
  def svmSubs          = ivy"org.scalameta::svm-subs:20.2.0"
  def utest            = ivy"com.lihaoyi::utest::0.7.5"
  def windowsAnsi      = ivy"io.github.alexarchambault.windows-ansi:windows-ansi:0.0.3"
}

object Versions {
  def fastParse = "2.3.0"
  def http4s = "0.18.26"
  def jniUtils = "0.2.2"
  def jsoniterScala = "2.9.1"
  def monadless = "0.0.13"
  def scalaz = "7.2.33"
}

def sbtCoursierVersion = "2.0.8"

def graalVmVersion = "21.1.0"

// should be the default index in the upcoming coursier release (> 2.0.16)
def jvmIndex = "https://github.com/coursier/jvm-index/raw/master/index.json"

object ScalaVersions {
  def scala213 = "2.13.6"
  def scala212 = "2.12.14"
  val all = Seq(scala213, scala212)

  // only used by the launcher module
  def scala211 = "2.11.12"

  def scalaJs = "1.5.1"
}
