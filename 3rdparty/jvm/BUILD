#  def quasiQuotes = "org.scalamacros" %% "quasiquotes" % "2.1.0"
#  def fastParse = "com.lihaoyi" %% "fastparse" % SharedVersions.fastParse
#  def jsoup = "org.jsoup" % "jsoup" % "1.10.3"
#  def scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
#  def scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % SharedVersions.scalaz
#  def caseApp = "com.github.alexarchambault" %% "case-app" % "1.1.3"
#  def caseApp12 = "com.github.alexarchambault" %% "case-app" % "1.2.0"
#  def http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % SharedVersions.http4s
#  def http4sDsl = "org.http4s" %% "http4s-dsl" % SharedVersions.http4s
#  def slf4jNop = "org.slf4j" % "slf4j-nop" % "1.7.25"
#  def okhttpUrlConnection = "com.squareup.okhttp" % "okhttp-urlconnection" % "2.7.5"
#  def sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
#  def typesafeConfig = "com.typesafe" % "config" % "1.3.2"
#  def argonautShapeless = "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M6"
#  def jackson = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4"

jar_library(
  name='scala-xml',
  jars=[
    scala_jar(org='org.scala-lang.modules', name='scala-xml', rev='1.0.6'),
  ],
)

jar_library(
  name='quasiQuotes',
  jars=[
    scala_jar(org='org.scalamacros', name='quasiquotes', rev='2.1.0'),
  ],
)

jar_library(
  name='fastParse',
  jars=[
    scala_jar(org='com.lihaoyi', name='fastparse', rev='1.0.0'),
  ],
)

jar_library(name='scalaz-concurrent',
            jars = [
              scala_jar(org='org.scalaz', name='scalaz-concurrent', rev='7.2.16'),
            ])

jar_library(name='scalaz-core',
            jars = [
              scala_jar(org='org.scalaz', name='scalaz-core', rev='7.2.16'),
            ])

jar_library(name='jsoup', jars=[jar(org='org.jsoup', name='jsoup', rev='1.10.3')])


SCALAJS_REV = '0.9.3'

jar_library(name = 'cli',
  jars = [
    scala_jar(org='org.scala-js', name='scalajs-cli', rev=SCALAJS_REV),
  ],
)

jar_library(name = 'compiler',
  jars = [
    jar(org='org.scala-js', name='scalajs-compiler_2.11.8', rev=SCALAJS_REV),
  ],
)

jar_library(name = 'library',
  jars = [
    scala_jar(org='org.scala-js', name='scalajs-library', rev=SCALAJS_REV),
  ],
)

jar_library(name = 'dom',
  jars = [
    scala_jar(org='org.scala-js', name='scalajs-dom_sjs0.6', rev='0.9.1'),
  ],
)
jar_library(name='jackson-module-scala',
            jars = [
              scala_jar(org='com.fasterxml.jackson.module', name='jackson-module-scala', rev='2.8.4'),
            ])


jar_library(name='caseapp',
            jars = [
              scala_jar(org='com.github.alexarchambault', name='case-app', rev='1.1.3'),
            ])

jar_library(name='argonaut-shapeless',
            jars = [
              scala_jar(org='com.github.alexarchambault', name='argonaut-shapeless_6.2', rev='1.2.0-M6'),
            ])


jar_library(name='soc',
            jars = [
              jar(org='io.github.soc', name='directories', rev='5'),
            ])

jar_library(name='scala-native',
            jars = [
              jar(org='org.scala-native', name='nativelib_native0.3_2.11', rev='0.3.3'),
              jar(org='io.get-coursier.scala-native', name='tools_2.11', rev='0.3.0-coursier-1'),
            ])
