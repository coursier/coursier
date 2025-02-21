import com.github.lolgab.mill.mima.Mima
import $file.^.deps, deps.{Deps, ScalaVersions}

import mill._, mill.scalalib._, mill.scalajslib._

import java.util.Locale

import scala.util.Properties

trait CsMima extends Mima {
  def mimaPreviousVersions: T[Seq[String]] = T.input {
    val current = os.proc("git", "describe", "--tags", "--match", "v*")
      .call()
      .out.trim()
    os.proc("git", "tag", "-l")
      .call()
      .out.lines()
      .filter(_ != current)
      .filter(_.startsWith("v"))
      .filter(!_.contains("-"))
      .map(_.stripPrefix("v"))
      .filter(!_.startsWith("0."))
      .filter(!_.startsWith("1."))
      .filter(!_.startsWith("2.0.")) // 2.1.x broke binary compatibility with 2.0.x
      .map(coursier.core.Version(_))
      .sorted
      .map(_.repr)
  }
}

lazy val latestTaggedVersion = os.proc("git", "describe", "--abbrev=0", "--tags", "--match", "v*")
  .call().out
  .trim()
def computeBuildVersion() = {
  val gitHead = os.proc("git", "rev-parse", "HEAD").call().out.trim()
  val maybeExactTag = scala.util.Try {
    os.proc("git", "describe", "--exact-match", "--tags", "--always", gitHead)
      .call().out
      .trim()
      .stripPrefix("v")
  }
  maybeExactTag.toOption.getOrElse {
    val commitsSinceTaggedVersion =
      os.proc("git", "rev-list", gitHead, "--not", latestTaggedVersion, "--count")
        .call().out.trim()
        .toInt
    val gitHash = os.proc("git", "rev-parse", "--short", "HEAD").call().out.trim()
    s"${latestTaggedVersion.stripPrefix("v")}-$commitsSinceTaggedVersion-$gitHash-SNAPSHOT"
  }
}
lazy val buildVersion = computeBuildVersion()

trait PublishLocalNoFluff extends PublishModule {
  def emptyZip = T {
    import java.io._
    import java.util.zip._
    val dest = T.dest / "empty.zip"
    val baos = new ByteArrayOutputStream
    val zos  = new ZipOutputStream(baos)
    zos.finish()
    zos.close()
    os.write(dest, baos.toByteArray)
    PathRef(dest)
  }
  // adapted from https://github.com/com-lihaoyi/mill/blob/fea79f0515dda1def83500f0f49993e93338c3de/scalalib/src/PublishModule.scala#L70-L85
  // writes empty zips as source and doc JARs
  def publishLocalNoFluff(localIvyRepo: String = null): define.Command[PathRef] = T.command {

    import mill.scalalib.publish.LocalIvyPublisher
    val publisher = localIvyRepo match {
      case null => LocalIvyPublisher
      case repo =>
        new LocalIvyPublisher(os.Path(repo.replace("{VERSION}", publishVersion()), T.workspace))
    }

    publisher.publishLocal(
      jar = jar().path,
      sourcesJar = emptyZip().path,
      docJar = emptyZip().path,
      pom = pom().path,
      ivy = ivy().path,
      artifact = artifactMetadata(),
      extras = extraPublish()
    )

    jar()
  }
}

trait CoursierJavaModule extends JavaModule {
  private def isArm64 =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("aarch64" | "arm64") => true
      case _                         => false
    }
  def javacSystemJvmId = T {
    if (Properties.isMac && isArm64) "zulu:8"
    else "adoptium:8"
  }
  def javacSystemJvm = T.source {
    val output = os.proc("cs", "java-home", "--jvm", javacSystemJvmId())
      .call(cwd = T.workspace)
      .out.trim()
    val javaHome = os.Path(output)
    assert(os.isDir(javaHome))
    PathRef(javaHome, quick = true)
  }
  // adds options equivalent to --release 8 + allowing access to unsupported JDK APIs
  // (no more straightforward options to achieve that AFAIK)
  def maybeJdk8JavacOpt = T {
    val javaHome   = javacSystemJvm().path
    val rtJar      = javaHome / "jre/lib/rt.jar"
    val hasModules = os.isDir(javaHome / "jmods")
    val hasRtJar   = os.isFile(rtJar)
    assert(hasModules || hasRtJar)
    if (hasModules)
      Seq("--system", javaHome.toString)
    else
      Seq("-source", "8", "-target", "8", "-bootclasspath", rtJar.toString)
  }
  def javacOptions = T {
    super.javacOptions() ++ maybeJdk8JavacOpt() ++ Seq(
      "-Xlint:unchecked"
    )
  }
}

trait CoursierPublishModule extends PublishModule with PublishLocalNoFluff with CoursierJavaModule {
  import mill.scalalib.publish._
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.get-coursier",
    url = "https://github.com/coursier/coursier",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("coursier", "coursier"),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault", "https://github.com/alexarchambault")
    )
  )
  def publishVersion = T.input(computeBuildVersion())
}

trait CsTests extends TestModule {
  def ivyDeps = super.ivyDeps() ++ Seq(
    Deps.pprint,
    Deps.utest
  )
  def testFramework = "utest.runner.Framework"
}

trait CsScalaJsModule extends ScalaJSModule with CsScalaModule {
  def scalaJSVersion = ScalaVersions.scalaJs
  def scalacOptions = super.scalacOptions() ++ Seq(
    "-P:scalajs:nowarnGlobalExecutionContext"
  )
}

trait CsResourcesTests extends TestModule {
  def testDataDir = T.source {
    PathRef(T.workspace / "modules" / "tests" / "shared" / "src" / "test" / "resources")
  }
  def forkEnv = super.forkEnv() ++ Seq(
    "COURSIER_TEST_DATA_DIR" ->
      testDataDir().path.toString,
    "COURSIER_TESTS_METADATA_DIR" ->
      (T.workspace / "modules" / "tests" / "metadata").toString,
    "COURSIER_TESTS_HANDMADE_METADATA_DIR" ->
      (T.workspace / "modules" / "tests" / "handmade-metadata" / "data").toString,
    "COURSIER_TESTS_METADATA_DIR_URI" ->
      (T.workspace / "modules" / "tests" / "metadata").toNIO.toUri.toASCIIString,
    "COURSIER_TESTS_HANDMADE_METADATA_DIR_URI" ->
      (T.workspace / "modules" / "tests" / "handmade-metadata" / "data").toNIO.toUri.toASCIIString
  )
}

trait JvmTests extends JavaModule with CsResourcesTests {
  def defaultCommandName() = "test"
  def sources = T.sources {
    val shared = Seq(
      millSourcePath / os.up / "shared" / "src" / "test" / "scala",
      millSourcePath / os.up / "jvm" / "src" / "test" / "scala"
    )
    super.sources() ++ shared.map(PathRef(_))
  }
}

trait JsTests extends TestScalaJSModule with CsResourcesTests {
  import mill.scalajslib.api._
  override def sources = T.sources {
    val shared = Seq(
      millSourcePath / os.up / "shared" / "src" / "test",
      millSourcePath / os.up / "js" / "src" / "test"
    )
    super.sources() ++ shared.map(PathRef(_))
  }
  def jsEnvConfig = T {
    super.jsEnvConfig() match {
      case node: JsEnvConfig.NodeJs =>
        node.copy(
          env = node.env ++ forkEnv()
        )
      case other =>
        System.err.println(s"Warning: don't know how to add env vars to JsEnvConfig $other")
        other
    }
  }
}

trait CsScalaModule extends ScalaModule {
  def scalacOptions = T {
    val sv = scalaVersion()
    val scala212Opts =
      if (sv.startsWith("2.12.")) Seq("-Ypartial-unification", "-language:higherKinds")
      else Nil
    val scala213Opts =
      if (sv.startsWith("2.13.")) Seq("-Ymacro-annotations", "-Wunused:nowarn")
      else Nil
    val scala2Opts =
      if (sv.startsWith("2.")) Seq("-Xasync")
      else Nil
    super.scalacOptions() ++ scala212Opts ++ scala213Opts ++ scala2Opts ++ Seq(
      "-deprecation",
      "-feature",
      "--release",
      "8"
    )
  }
  def scalacPluginIvyDeps = T {
    val sv = scalaVersion()
    val scala212Plugins =
      if (sv.startsWith("2.12.")) Agg(Deps.macroParadise)
      else Nil
    super.scalacPluginIvyDeps() ++ scala212Plugins
  }
}

trait CsModule extends SbtModule with CsScalaModule with CoursierJavaModule {
  def sources = T.sources {
    val sbv    = mill.scalalib.api.ZincWorkerUtil.scalaBinaryVersion(scalaVersion())
    val parent = super.sources()
    val extra = parent.map(_.path).filter(_.last == "scala").flatMap { p =>
      val dirNames = Seq(s"scala-$sbv")
      dirNames.map(n => PathRef(p / os.up / n))
    }
    parent ++ extra
  }
  def runClasspath = Task {
    localClasspath() ++ transitiveLocalClasspath() ++ super.runClasspath()
  }
}

trait CsCrossJvmJsModule extends CrossSbtModule with CsModule {
  def sources = T.sources {
    val shared = PathRef(millSourcePath / os.up / "shared" / "src" / "main")
    super.sources() ++ Seq(shared)
  }
}
