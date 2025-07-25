import mill._, scalalib._

object Deps {
  def argonautShapeless = ivy"com.github.alexarchambault::argonaut-shapeless_6.3::1.3.1"
  def caseApp           = ivy"com.github.alexarchambault::case-app:2.1.0-M30"
  def catsCore          = ivy"org.typelevel::cats-core:${Versions.cats}"
  def catsFree213       = ivy"org.typelevel:cats-free_2.13:${Versions.cats}"
  def catsEffect        = ivy"org.typelevel::cats-effect::3.6.1"
  def classPathUtil     = ivy"io.get-coursier::class-path-util:0.1.4"
  def collectionCompat  = ivy"org.scala-lang.modules::scala-collection-compat::2.13.0"
  def concurrentReferenceHashMap =
    ivy"io.github.alexarchambault:concurrent-reference-hash-map:1.1.0"
  def dataClass         = ivy"io.github.alexarchambault::data-class:0.2.7"
  def dependency        = ivy"io.get-coursier::dependency::0.3.2"
  def directories       = ivy"io.get-coursier.util:directories-jni:0.1.4"
  def diffUtils         = ivy"io.github.java-diff-utils:java-diff-utils:4.15"
  def dockerClient      = ivy"com.spotify:docker-client:8.16.0"
  def fastParse         = ivy"com.lihaoyi::fastparse::3.1.1"
  def http4sBlazeServer = ivy"org.http4s::http4s-blaze-server:0.23.17"
  def http4sDsl         = ivy"org.http4s::http4s-dsl:${Versions.http4s}"
  def http4sServer      = ivy"org.http4s::http4s-server:${Versions.http4s}"
  def isTerminal        = ivy"io.github.alexarchambault:is-terminal:0.1.2"
  def java8Compat       = ivy"org.scala-lang.modules::scala-java8-compat:1.0.2"
  def jimfs             = ivy"com.google.jimfs:jimfs:1.3.1"
  def jna               = ivy"net.java.dev.jna:jna:5.17.0"
  def jniUtils          = ivy"io.get-coursier.jniutils:windows-jni-utils:${Versions.jniUtils}"
  def jniUtilsBootstrap =
    ivy"io.get-coursier.jniutils:windows-jni-utils-bootstrap:${Versions.jniUtils}"
  def jol  = ivy"org.openjdk.jol:jol-core:0.17"
  def jsch = ivy"com.github.mwiede:jsch:0.2.25"
  def jsoniterCore =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::${Versions.jsoniterScala}"
  def jsoniterMacros =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
  def jsoup          = ivy"org.jsoup:jsoup:1.20.1"
  def logbackClassic = ivy"ch.qos.logback:logback-classic:1.5.18"
  def macroParadise  = ivy"org.scalamacros:::paradise:2.1.1"
  def mdoc           = ivy"org.scalameta::mdoc:2.7.0"
  def noCrcZis       = ivy"io.github.alexarchambault.scala-cli.tmp:zip-input-stream:0.1.1"
  def osLib          = ivy"com.lihaoyi::os-lib:0.11.4"
  def plexusArchiver = ivy"org.codehaus.plexus:plexus-archiver:4.10.0"
  // plexus-archiver needs its loggers
  def plexusContainerDefault = ivy"org.codehaus.plexus:plexus-container-default:2.1.1"
    .exclude("junit" -> "junit")
  def pprint           = ivy"com.lihaoyi::pprint::0.9.1"
  def proguard         = ivy"com.guardsquare:proguard-base:7.7.0"
  def pythonNativeLibs = ivy"ai.kien::python-native-libs:0.2.4"
  def scalaAsync       = ivy"org.scala-lang.modules::scala-async::1.0.1"
  def scalaCliConfig(sv: String) =
    if (sv.startsWith("2.12"))
      ivy"org.virtuslab.scala-cli::config:1.1.3"
    else
      ivy"org.virtuslab.scala-cli:config_3:1.8.3"
        .exclude(("com.github.plokhotnyuk.jsoniter-scala", "jsoniter-scala-core_3"))
  def scalaJsDom               = ivy"org.scala-js::scalajs-dom::2.4.0"
  def scalaJsReact             = ivy"com.github.japgolly.scalajs-react::core::2.1.2"
  def scalaNativeTools03       = ivy"org.scala-native::tools:0.3.9"
  def scalaNativeTools040M2    = ivy"org.scala-native::tools:0.4.0-M2"
  def scalaNativeTools040      = ivy"org.scala-native::tools:0.4.17"
  def scalaReflect(sv: String) = ivy"org.scala-lang:scala-reflect:$sv"
  def scalaXml                 = ivy"org.scala-lang.modules::scala-xml:2.4.0"
  def scalazCore               = ivy"org.scalaz::scalaz-core::${Versions.scalaz}"
  def scalazConcurrent         = ivy"org.scalaz::scalaz-concurrent:${Versions.scalaz}"
  def scodec                   = ivy"org.scodec::scodec-core:2.3.2"
  def shapeless                = ivy"com.chuusai::shapeless:2.3.12"
  def slf4JNop                 = ivy"org.slf4j:slf4j-nop:2.0.17"
  def svm                      = ivy"org.graalvm.nativeimage:svm:21.3.13"
  def tika                     = ivy"org.apache.tika:tika-core:3.2.1"
  def ujson                    = ivy"com.lihaoyi::ujson:4.2.1"
  def utest                    = ivy"com.lihaoyi::utest::0.8.9"
  def versions                 = ivy"io.get-coursier::versions::0.5.1"
  def windowsAnsi              = ivy"io.github.alexarchambault.windows-ansi:windows-ansi:0.0.6"
  def windowsAnsiPs =
    ivy"io.github.alexarchambault.windows-ansi:windows-ansi-ps:${windowsAnsi.version}"
  def zstdJni = ivy"com.github.luben:zstd-jni:1.5.7-3"
}

object Versions {
  def cats          = "2.13.0"
  def http4s        = "0.23.30"
  def jniUtils      = "0.3.3"
  def jsoniterScala = "2.13.5"
  def scalaz        = "7.2.36"
}

def sbtCoursierVersion = "2.1.4"

def graalVmJvmId = "liberica-nik:21.0.5"

def scalaCliVersion = "1.5.1"

def csDockerVersion = "2.1.23"

object ScalaVersions {
  def scala3   = "3.3.6"
  def scala213 = "2.13.16"
  def scala212 = "2.12.20"
  val all      = Seq(scala213, scala212)

  def scalaJs = "1.18.1"
}

object Docker {
  def customMuslBuilderImageName = "scala-cli-base-musl"
  def muslBuilder                = s"$customMuslBuilderImageName:latest"
  def alpineImage                = "alpine:3.21.2"
  def alpineJavaImage            = "eclipse-temurin:21-jre-alpine"
}
