package coursierbuild

import mill._, scalalib._

object Deps {
  object Deps {
    def argonautShapeless = mvn"com.github.alexarchambault::argonaut-shapeless_6.3::1.3.1"
    def caseApp           = mvn"com.github.alexarchambault::case-app:2.1.0"
    def catsCore          = mvn"org.typelevel::cats-core:${Versions.cats}"
    def catsFree213       = mvn"org.typelevel:cats-free_2.13:${Versions.cats}"
    def catsEffect        = mvn"org.typelevel::cats-effect::3.6.1"
    def classPathUtil     = mvn"io.get-coursier::class-path-util:0.1.4"
    def collectionCompat  = mvn"org.scala-lang.modules::scala-collection-compat::2.13.0"
    def concurrentReferenceHashMap =
      mvn"io.github.alexarchambault:concurrent-reference-hash-map:1.1.0"
    def dataClass         = mvn"io.github.alexarchambault::data-class:0.2.7"
    def dependency        = mvn"io.get-coursier::dependency::0.3.2"
    def directories       = mvn"io.get-coursier.util:directories-jni:0.1.4"
    def diffUtils         = mvn"io.github.java-diff-utils:java-diff-utils:4.15"
    def dockerClient      = mvn"com.spotify:docker-client:8.16.0"
    def fastParse         = mvn"com.lihaoyi::fastparse::3.1.1"
    def http4sBlazeServer = mvn"org.http4s::http4s-blaze-server:0.23.17"
    def http4sDsl         = mvn"org.http4s::http4s-dsl:${Versions.http4s}"
    def http4sServer      = mvn"org.http4s::http4s-server:${Versions.http4s}"
    def isTerminal        = mvn"io.github.alexarchambault:is-terminal:0.1.2"
    def java8Compat       = mvn"org.scala-lang.modules::scala-java8-compat:1.0.2"
    def jimfs             = mvn"com.google.jimfs:jimfs:1.3.1"
    def jna               = mvn"net.java.dev.jna:jna:5.17.0"
    def jniUtils          = mvn"io.get-coursier.jniutils:windows-jni-utils:${Versions.jniUtils}"
    def jniUtilsBootstrap =
      mvn"io.get-coursier.jniutils:windows-jni-utils-bootstrap:${Versions.jniUtils}"
    def jol  = mvn"org.openjdk.jol:jol-core:0.17"
    def jsch = mvn"com.github.mwiede:jsch:0.2.25"
    def jsoniterCore =
      mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::${Versions.jsoniterScala}"
    def jsoniterMacros =
      mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
    def jsoup          = mvn"org.jsoup:jsoup:1.21.2"
    def logbackClassic = mvn"ch.qos.logback:logback-classic:1.5.18"
    def macroParadise  = mvn"org.scalamacros:::paradise:2.1.1"
    def mdoc           = mvn"org.scalameta::mdoc:2.7.2"
    def noCrcZis       = mvn"io.github.alexarchambault.scala-cli.tmp:zip-input-stream:0.1.1"
    def osLib          = mvn"com.lihaoyi::os-lib:0.11.5"
    def plexusArchiver = mvn"org.codehaus.plexus:plexus-archiver:4.10.1"
    // plexus-archiver needs its loggers
    def plexusContainerDefault = mvn"org.codehaus.plexus:plexus-container-default:2.1.1"
      .exclude("junit" -> "junit")
    def pprint           = mvn"com.lihaoyi::pprint::0.9.0"
    def proguard         = mvn"com.guardsquare:proguard-base:7.7.0"
    def pythonNativeLibs = mvn"ai.kien::python-native-libs:0.2.4"
    def scalaAsync       = mvn"org.scala-lang.modules::scala-async::1.0.1"
    def scalaCliConfig(sv: String) =
      if (sv.startsWith("2.12"))
        mvn"org.virtuslab.scala-cli::config:1.1.3"
      else
        mvn"org.virtuslab.scala-cli:config_3:1.9.0"
          .exclude(("com.github.plokhotnyuk.jsoniter-scala", "jsoniter-scala-core_3"))
    def scalaJsDom               = mvn"org.scala-js::scalajs-dom::2.4.0"
    def scalaJsReact             = mvn"com.github.japgolly.scalajs-react::core::2.1.2"
    def scalaNativeTools03       = mvn"org.scala-native::tools:0.3.9"
    def scalaNativeTools040M2    = mvn"org.scala-native::tools:0.4.0-M2"
    def scalaNativeTools040      = mvn"org.scala-native::tools:0.4.17"
    def scalaReflect(sv: String) = mvn"org.scala-lang:scala-reflect:$sv"
    def scalaXml                 = mvn"org.scala-lang.modules::scala-xml:2.4.0"
    def scalazCore               = mvn"org.scalaz::scalaz-core::${Versions.scalaz}"
    def scalazConcurrent         = mvn"org.scalaz::scalaz-concurrent:${Versions.scalaz}"
    def scodec                   = mvn"org.scodec::scodec-core:2.3.3"
    def shapeless                = mvn"com.chuusai::shapeless:2.3.12"
    def slf4JNop                 = mvn"org.slf4j:slf4j-nop:2.0.17"
    def svm                      = mvn"org.graalvm.nativeimage:svm:21.3.15"
    def tika                     = mvn"org.apache.tika:tika-core:3.2.2"
    def ujson                    = mvn"com.lihaoyi::ujson:4.3.2"
    def utest                    = mvn"com.lihaoyi::utest::0.9.1"
    def versions                 = mvn"io.get-coursier::versions::0.5.1"
    def windowsAnsi              = mvn"io.github.alexarchambault.windows-ansi:windows-ansi:0.0.6"
    def windowsAnsiPs =
      mvn"io.github.alexarchambault.windows-ansi:windows-ansi-ps:${windowsAnsi.version}"
    def zstdJni = mvn"com.github.luben:zstd-jni:1.5.7-4"
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

    def scalaJs = "1.19.0"
  }

  object Docker {
    def customMuslBuilderImageName = "scala-cli-base-musl"
    def muslBuilder                = s"$customMuslBuilderImageName:latest"
    def alpineImage                = "alpine:3.21.2"
    def alpineJavaImage            = "eclipse-temurin:21-jre-alpine"
  }
}
