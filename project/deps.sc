import mill._, scalalib._

object Deps {
  def argonautShapeless = ivy"com.github.alexarchambault::argonaut-shapeless_6.3::1.3.1"
  def caseApp           = ivy"com.github.alexarchambault::case-app:2.1.0-M28"
  def catsCore          = ivy"org.typelevel::cats-core:${Versions.cats}"
  def catsFree          = ivy"org.typelevel::cats-free:${Versions.cats}"
  def catsEffect        = ivy"org.typelevel::cats-effect::3.5.3"
  def classPathUtil     = ivy"io.get-coursier::class-path-util:0.1.4"
  def collectionCompat  = ivy"org.scala-lang.modules::scala-collection-compat::2.12.0"
  def concurrentReferenceHashMap =
    ivy"io.github.alexarchambault:concurrent-reference-hash-map:1.1.0"
  def dataClass         = ivy"io.github.alexarchambault::data-class:0.2.6"
  def dockerClient      = ivy"com.spotify:docker-client:8.16.0"
  def fastParse         = ivy"com.lihaoyi::fastparse::${Versions.fastParse}"
  def http4sBlazeServer = ivy"org.http4s::http4s-blaze-server:0.23.15"
  def http4sDsl         = ivy"org.http4s::http4s-dsl:${Versions.http4s}"
  def http4sServer      = ivy"org.http4s::http4s-server:${Versions.http4s}"
  def java8Compat       = ivy"org.scala-lang.modules::scala-java8-compat:1.0.2"
  def jimfs             = ivy"com.google.jimfs:jimfs:1.3.0"
  def jniUtils          = ivy"io.get-coursier.jniutils:windows-jni-utils:${Versions.jniUtils}"
  def jniUtilsBootstrap =
    ivy"io.get-coursier.jniutils:windows-jni-utils-bootstrap:${Versions.jniUtils}"
  def jol = ivy"org.openjdk.jol:jol-core:0.17"
  def jsoniterCore =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core::${Versions.jsoniterScala}"
  def jsoniterMacros =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
  def jsoup          = ivy"org.jsoup:jsoup:1.18.1"
  def logbackClassic = ivy"ch.qos.logback:logback-classic:1.2.13"
  def macroParadise  = ivy"org.scalamacros:::paradise:2.1.1"
  def mdoc           = ivy"org.scalameta::mdoc:2.5.4"
  def noCrcZis       = ivy"io.github.alexarchambault.scala-cli.tmp:zip-input-stream:0.1.1"
  def osLib          = ivy"com.lihaoyi::os-lib:0.10.3"
  def plexusArchiver = ivy"org.codehaus.plexus:plexus-archiver:4.9.0"
  // plexus-archiver needs its loggers
  def plexusContainerDefault = ivy"org.codehaus.plexus:plexus-container-default:2.1.1"
    .exclude("junit" -> "junit")
  def pprint                   = ivy"com.lihaoyi::pprint:0.9.0"
  def proguard                 = ivy"com.guardsquare:proguard-base:7.5.0"
  def pythonNativeLibs         = ivy"ai.kien::python-native-libs:0.2.4"
  def scalaAsync               = ivy"org.scala-lang.modules::scala-async:0.10.0"
  def scalaCliConfig           = ivy"org.virtuslab.scala-cli::config:0.2.1"
  def scalaJsDom               = ivy"org.scala-js::scalajs-dom::2.4.0"
  def scalaJsReact             = ivy"com.github.japgolly.scalajs-react::core::2.1.2"
  def scalaNativeTools03       = ivy"org.scala-native::tools:0.3.9"
  def scalaNativeTools040M2    = ivy"org.scala-native::tools:0.4.0-M2"
  def scalaNativeTools040      = ivy"org.scala-native::tools:0.4.17"
  def scalaReflect(sv: String) = ivy"org.scala-lang:scala-reflect:$sv"
  def scalaXml                 = ivy"org.scala-lang.modules::scala-xml:2.3.0"
  def scalazCore               = ivy"org.scalaz::scalaz-core::${Versions.scalaz}"
  def scalazConcurrent         = ivy"org.scalaz::scalaz-concurrent:${Versions.scalaz}"
  def slf4JNop                 = ivy"org.slf4j:slf4j-nop:2.0.13"
  def svm                      = ivy"org.graalvm.nativeimage:svm:22.0.0.2"
  def ujson                    = ivy"com.lihaoyi::ujson:3.3.1"
  def utest                    = ivy"com.lihaoyi::utest::0.8.4"
  def windowsAnsi              = ivy"io.github.alexarchambault.windows-ansi:windows-ansi:0.0.5"
  def windowsAnsiPs =
    ivy"io.github.alexarchambault.windows-ansi:windows-ansi-ps:${windowsAnsi.version}"
}

object Versions {
  def cats          = "2.12.0"
  def fastParse     = "3.1.0"
  def http4s        = "0.23.27"
  def jniUtils      = "0.3.3"
  def jsoniterScala = "2.13.5"
  def scalaz        = "7.2.36"
}

def sbtCoursierVersion = "2.1.4"

def graalVmVersion = "22.3.0"
def graalVmJvmId   = s"graalvm-java17:$graalVmVersion"

def scalaCliVersion = "1.0.0-RC1"

// should be the default index in the upcoming coursier release (> 2.0.16)
def jvmIndex = "https://github.com/coursier/jvm-index/raw/master/index.json"

def csDockerVersion = "2.1.0-RC1"

object ScalaVersions {
  def scala213 = "2.13.12"
  def scala212 = "2.12.18"
  val all      = Seq(scala213, scala212)

  def scalaJs = "1.12.0"
}

object Docker {
  def customMuslBuilderImageName = "scala-cli-base-musl"
  def muslBuilder                = s"$customMuslBuilderImageName:latest"
}
