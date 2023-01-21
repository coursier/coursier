import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $file.project.deps, deps.{Deps, ScalaVersions}
import $file.project.docs
import $file.project.ghreleaseassets
import $file.project.launchers, launchers.{Launchers, platformBootstrapExtension}
import $file.project.modules.`bootstrap-launcher0`, `bootstrap-launcher0`.BootstrapLauncher
import $file.project.modules.cache0, cache0.{Cache, CacheJvmBase}
import $file.project.modules.core0, core0.{Core, CoreJvmBase}
import $file.project.modules.coursier0, coursier0.{Coursier, CoursierJvmBase, CoursierTests}
import $file.project.modules.directories0, directories0.Directories
import $file.project.modules.doc0, doc0.Doc
import $file.project.modules.interop0, interop0.{Cats, Scalaz}
import $file.project.modules.launcher0, launcher0.LauncherBase
import $file.project.modules.shared, shared.{
  buildVersion,
  CoursierPublishModule,
  CsCrossJvmJsModule,
  CsMima,
  CsModule,
  CsScalaJsModule,
  CsTests,
  JsTests,
  JvmTests
}
import $file.project.modules.tests0, tests0.TestsModule
import $file.project.modules.util0, util0.{Util, UtilJvmBase}
import $file.project.publishing, publishing.mavenOrg
import $file.project.relativize, relativize.{relativize => doRelativize}
import $file.project.sync
import $file.project.workers

import mill._
import mill.scalalib._
import mill.scalajslib._
import mill.contrib.bloop.Bloop

import scala.concurrent.duration._
import scala.util.Properties

// Tell mill modules are under modules/
implicit def millModuleBasePath: define.BasePath =
  define.BasePath(super.millModuleBasePath.value / "modules")

object util extends Module {
  object jvm extends Cross[UtilJvm](ScalaVersions.all: _*)
  object js  extends Cross[UtilJs](ScalaVersions.all: _*)
}
object core extends Module {
  object jvm extends Cross[CoreJvm](ScalaVersions.all: _*)
  object js  extends Cross[CoreJs](ScalaVersions.all: _*)
}
object cache extends Module {
  object jvm extends Cross[CacheJvm](ScalaVersions.all: _*)
  object js  extends Cross[CacheJs](ScalaVersions.all: _*)
}
object launcher                extends Cross[Launcher](ScalaVersions.all: _*)
object env                     extends Cross[Env](ScalaVersions.all: _*)
object `launcher-native_03`    extends LauncherNative03
object `launcher-native_040M2` extends LauncherNative040M2
object `launcher-native_04`    extends LauncherNative04

object coursier extends Module {
  object jvm extends Cross[CoursierJvm](ScalaVersions.all: _*)
  object js  extends Cross[CoursierJs](ScalaVersions.all: _*)
}

object directories extends Directories

object `proxy-setup` extends JavaModule with CoursierPublishModule {
  def artifactName = "coursier-proxy-setup"
}

object paths extends JavaModule {
  def moduleDeps = Seq(
    directories
  )
  def ivyDeps = Agg(
    Deps.jniUtils
  )
}
object `windows-ansi` extends Module {
  object ps extends JavaModule
}

object `custom-protocol-for-test` extends CsModule {
  def scalaVersion = ScalaVersions.scala213
}

object `bootstrap-launcher` extends BootstrapLauncher { self =>
  def proxySources = T.sources {
    val dest = T.dest / "sources"
    val orig = `proxy-setup`.sources()
    for ((pathRef, idx) <- orig.zipWithIndex)
      os.copy.into(pathRef.path, dest / s"dir-$idx", copyAttributes = true, createFolders = true)
    os.walk(dest)
      .filter(_.last.endsWith(".java"))
      .filter(os.isFile(_))
      .foreach { f =>
        val content = os.read(f)
          .replaceAll("package coursier.proxy;", "package coursier.bootstrap.launcher.proxy;")
        os.write.over(f, content)
      }
    Seq(PathRef(dest))
  }
  def sources = T.sources {
    super.sources() ++
      directories.sources() ++
      paths.sources() ++
      proxySources() ++
      `windows-ansi`.ps.sources()
  }
  def resources = T.sources {
    super.resources() ++ directories.resources()
  }
  def proguardClassPath = T {
    proguard.runClasspath()
  }
  object test extends Tests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Seq(
      Deps.collectionCompat,
      Deps.java8Compat
    )
  }
  object it extends Tests with CsTests {
    def sources = T.sources(
      millSourcePath / "src" / "it" / "scala",
      millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = Seq(
      self.test
    )
    def forkArgs = T.input {
      val testRepoServer0 = workers.testRepoServer()
      super.forkArgs() ++ Seq(
        s"-Dtest.repository=${testRepoServer0.url}",
        s"-Dtest.repository.user=${testRepoServer0.user}",
        s"-Dtest.repository.password=${testRepoServer0.password}"
      )
    }
  }
}

object proguard extends JavaModule {
  def ivyDeps = Agg(
    Deps.proguard
  )
}

object tests extends Module {
  object jvm extends Cross[TestsJvm](ScalaVersions.all: _*)
  object js  extends Cross[TestsJs](ScalaVersions.all: _*)
}

object `proxy-tests` extends Cross[ProxyTests](ScalaVersions.all: _*)

object interop extends Module {
  object scalaz extends Module {
    object jvm extends Cross[ScalazJvm](ScalaVersions.all: _*)
    object js  extends Cross[ScalazJs](ScalaVersions.all: _*)
  }
  object cats extends Module {
    object jvm extends Cross[CatsJvm](ScalaVersions.all: _*)
    object js  extends Cross[CatsJs](ScalaVersions.all: _*)
  }
}

object jvm     extends Cross[Jvm](ScalaVersions.all: _*)
object install extends Cross[Install](ScalaVersions.all: _*)

object cli         extends Cli
object `cli-tests` extends CliTests

object web extends Web

class UtilJvm(val crossScalaVersion: String) extends UtilJvmBase {
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.jsoup
  )
}
class UtilJs(val crossScalaVersion: String) extends CsScalaJsModule with Util

class CoreJvm(val crossScalaVersion: String) extends CoreJvmBase {
  def moduleDeps = super.moduleDeps ++ Seq(
    util.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.concurrentReferenceHashMap,
    Deps.scalaXml
  )
  object test extends Tests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.jol
    )
  }
}
class CoreJs(val crossScalaVersion: String) extends Core with CsScalaJsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    util.js()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaJsDom
  )
  object test extends Tests with CsTests
}

class CacheJvm(val crossScalaVersion: String) extends CacheJvmBase {
  def moduleDeps = Seq(
    util.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.jniUtils,
    Deps.plexusArchiver,
    Deps.plexusContainerDefault,
    Deps.scalaCliConfig,
    Deps.windowsAnsi
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.jsoniterMacros,
    Deps.svm
  )
  def sources = T.sources {
    super.sources() ++ directories.sources() ++ paths.sources()
  }
  def resources = T.sources {
    super.resources() ++ directories.resources()
  }
  def customLoaderCp = T {
    `custom-protocol-for-test`.runClasspath()
  }
  object test extends Tests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.http4sBlazeServer,
      Deps.http4sDsl,
      Deps.logbackClassic,
      Deps.osLib,
      Deps.pprint,
      Deps.scalaAsync
    )
  }
}
class CacheJs(val crossScalaVersion: String) extends Cache with CsScalaJsModule {
  def moduleDeps = Seq(
    util.js()
  )
  def artifactName = "fetch-js"
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaJsDom
  )
}

class Launcher(val crossScalaVersion: String) extends LauncherBase {
  def ivyDeps = super.ivyDeps() ++ Seq(
    Deps.collectionCompat,
    Deps.noCrcZis,
    Deps.pythonNativeLibs
  )
  def compileIvyDeps = Agg(
    Deps.dataClass
  )

  def bootstrap                   = `bootstrap-launcher`.proguardedAssembly()
  def resourceBootstrap           = `bootstrap-launcher`.proguardedResourceAssembly()
  def noProguardBootstrap         = `bootstrap-launcher`.assembly()
  def noProguardResourceBootstrap = `bootstrap-launcher`.resourceAssembly()
}

class Env(val crossScalaVersion: String) extends CrossSbtModule with CsModule
    with CoursierPublishModule with CsMima {
  def mimaPreviousVersions = T {
    super.mimaPreviousVersions().filter(_ != "2.0.16")
  }
  def artifactName = "coursier-env"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.collectionCompat,
    Deps.jniUtils
  )
  object test extends Tests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.jimfs
    )
  }
}

def cliScalaVersion = ScalaVersions.scala212
def launcherModule  = launcher
trait LauncherNative03 extends CsModule with CoursierPublishModule {
  def artifactName = "coursier-launcher-native_0.3"
  def scalaVersion = cliScalaVersion
  def compileModuleDeps = Seq(
    launcherModule(cliScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaNativeTools03
  )
}
trait LauncherNative040M2 extends CsModule with CoursierPublishModule {
  def artifactName = "coursier-launcher-native_0.4.0-M2"
  def scalaVersion = cliScalaVersion
  def compileModuleDeps = Seq(
    launcherModule(cliScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaNativeTools040M2
  )
}
trait LauncherNative04 extends CsModule with CoursierPublishModule {
  def artifactName = "coursier-launcher-native_0.4"
  def scalaVersion = cliScalaVersion
  def compileModuleDeps = Seq(
    launcherModule(cliScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaNativeTools040
  )
}

class CoursierJvm(val crossScalaVersion: String) extends CoursierJvmBase { self =>
  def moduleDeps = Seq(
    core.jvm(),
    cache.jvm(),
    `proxy-setup`
  )
  // Put CoursierTests right after TestModule, and see what happens
  object test extends TestModule with Tests with CoursierTests with CsTests with JvmTests
  object it extends TestModule with Tests with CoursierTests with CsTests with JvmTests {
    def sources = T.sources(
      millSourcePath / "src" / "it" / "scala",
      millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = super.moduleDeps ++ Seq(
      self.test
    )
    def forkArgs = T.input {
      val testRepoServer0 = workers.testRepoServer()
      super.forkArgs() ++ Seq(
        s"-Dtest.repository=${testRepoServer0.url}",
        s"-Dtest.repository.user=${testRepoServer0.user}",
        s"-Dtest.repository.password=${testRepoServer0.password}"
      )
    }
  }
}
class CoursierJs(val crossScalaVersion: String) extends Coursier with CsScalaJsModule {
  def moduleDeps = Seq(
    core.js(),
    cache.js()
  )
  object test extends Tests with CsTests with JsTests with CoursierTests
}

class TestsJvm(val crossScalaVersion: String) extends TestsModule { self =>
  def moduleDeps = super.moduleDeps ++ Seq(
    core.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.jsoup
  )
  object test extends Tests with CsTests with JvmTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.jvm()
    )
  }
  object it extends Tests with CsTests with JvmTests with workers.UsesRedirectingServer {
    def redirectingServerCp =
      `redirecting-server`.runClasspath()
    def redirectingServerMainClass =
      `redirecting-server`.mainClass().getOrElse(sys.error("no main class"))
    def forkArgs = T.input {
      val redirectingServer0 = redirectingServer()
      val testRepoServer0    = workers.testRepoServer()
      super.forkArgs() ++ Seq(
        s"-Dtest.redirect.repository=${redirectingServer0.url}",
        s"-Dtest.repository=${testRepoServer0.url}",
        s"-Dtest.repository.user=${testRepoServer0.user}",
        s"-Dtest.repository.password=${testRepoServer0.password}"
      )
    }
    def sources = T.sources(
      millSourcePath / "src" / "it" / "scala",
      millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.jvm(),
      self.test
    )
  }
}
class TestsJs(val crossScalaVersion: String) extends TestsModule with CsScalaJsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    core.js()
  )
  // testOptions := testOptions.dependsOn(runNpmInstallIfNeeded).value
  object test extends Tests with CsTests with JsTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.js()
    )
  }
}

class ProxyTests(val crossScalaVersion: String) extends CrossSbtModule with CsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    `proxy-setup`
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.dockerClient,
    Deps.scalaAsync,
    Deps.slf4JNop
  )
  object it extends Tests with CsTests {
    def sources = T.sources(
      millSourcePath / "src" / "it" / "scala",
      millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = super.moduleDeps ++ Seq(
      tests.jvm().test
    )
    def testFramework = "coursier.test.CustomFramework"
  }
  // sharedTestResources
}

class ScalazJvm(val crossScalaVersion: String) extends Scalaz with CsMima {
  def moduleDeps = Seq(
    cache.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalazConcurrent
  )
  object test extends Tests with CsTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      tests.jvm().test
    )
  }
}
class ScalazJs(val crossScalaVersion: String) extends Scalaz with CsScalaJsModule {
  def moduleDeps = Seq(
    cache.js()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalazCore
  )
}

class CatsJvm(val crossScalaVersion: String) extends Cats with CsMima {
  def moduleDeps = Seq(
    cache.jvm()
  )
  object test extends Tests with CsTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      tests.jvm().test
    )
  }
}
class CatsJs(val crossScalaVersion: String) extends Cats with CsScalaJsModule {
  def moduleDeps = Seq(
    cache.js()
  )
}

class Install(val crossScalaVersion: String) extends CrossSbtModule with CsModule
    with CoursierPublishModule with CsMima {
  def mimaPreviousVersions = T {
    super.mimaPreviousVersions().filter(_ != "2.0.16")
  }
  def artifactName = "coursier-install"
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.jvm(),
    env(),
    jvm(),
    launcherModule()
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.argonautShapeless,
    Deps.catsCore
  )
  object test extends Tests with CsTests
}

class Jvm(val crossScalaVersion: String) extends CrossSbtModule with CsModule
    with CoursierPublishModule with CsMima {
  def mimaPreviousVersions = T {
    super.mimaPreviousVersions().filter(_ != "2.0.16")
  }
  def artifactName = "coursier-jvm"
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.jvm(),
    env()
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass,
    Deps.jsoniterMacros,
    Deps.svm
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.argonautShapeless,
    Deps.jsoniterCore
  )
  object test extends Tests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Seq(
      Deps.osLib
    )
  }
}

trait Cli extends CsModule with CoursierPublishModule with Launchers {
  def artifactName = "coursier-cli"
  def scalaVersion = cliScalaVersion
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.jvm(cliScalaVersion),
    install(cliScalaVersion),
    jvm(cliScalaVersion),
    launcherModule(cliScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.argonautShapeless,
    Deps.caseApp,
    Deps.catsCore,
    Deps.catsFree,
    Deps.dataClass,
    Deps.monadlessCats,
    Deps.monadlessStdlib,
    Deps.noCrcZis,
    ivy"com.chuusai::shapeless:2.3.10",
    Deps.slf4JNop
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.svm
  )
  def mainClass = Some("coursier.cli.Coursier")
  def finalMainClassOpt = T {
    Right("coursier.cli.Coursier"): Either[String, String]
  }
  def manifest = T {
    import java.util.jar.Attributes.Name
    val ver = publishVersion()
    super.manifest().add(
      Name.IMPLEMENTATION_TITLE.toString     -> "coursier-cli",
      Name.IMPLEMENTATION_VERSION.toString   -> ver,
      Name.SPECIFICATION_VENDOR.toString     -> "io.get-coursier",
      Name.SPECIFICATION_TITLE.toString      -> "coursier-cli",
      Name.IMPLEMENTATION_VENDOR_ID.toString -> "io.get-coursier",
      Name.SPECIFICATION_VERSION.toString    -> ver,
      Name.IMPLEMENTATION_VENDOR.toString    -> "io.get-coursier"
    )
  }
  def docJar = T {
    val jar  = T.dest / "empty.jar"
    val baos = new java.io.ByteArrayOutputStream
    val zos  = new java.util.zip.ZipOutputStream(baos)
    zos.finish()
    zos.close()
    os.write.over(jar, baos.toByteArray)
    PathRef(jar)
  }
  object test extends Tests with CsTests
}

trait CliTests extends CsModule with CoursierPublishModule { self =>
  def scalaVersion = cliScalaVersion
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.jvm(cliScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.caseApp,
    Deps.dockerClient,
    Deps.osLib,
    Deps.pprint,
    Deps.ujson,
    Deps.utest
  )
  object test extends Tests with CsTests {
    def forkArgs = {
      val launcherTask = cli.launcher.map(_.path)
      val assemblyTask = cli.assembly.map(_.path)
      T {
        super.forkArgs() ++ Seq(
          s"-Dcoursier-test-launcher=${launcherTask()}",
          s"-Dcoursier-test-assembly=${assemblyTask()}",
          "-Dcoursier-test-launcher-accepts-D=false",
          "-Dcoursier-test-launcher-accepts-J=false"
        )
      }
    }
  }
  trait NativeTests extends Tests with CsTests with Bloop.Module {
    def cliLauncher: T[PathRef]
    def skipBloop = true
    def sources = T.sources {
      super.sources() ++ self.test.sources()
    }
    def forkArgs = {
      val launcherTask = cliLauncher.map(_.path)
      T {
        val launcher = launcherTask()
        super.forkArgs() ++ Seq(
          s"-Dcoursier-test-launcher=$launcher",
          s"-Dcoursier-test-assembly=$launcher",
          "-Dcoursier-test-launcher-accepts-D=false",
          "-Dcoursier-test-launcher-accepts-J=false"
        )
      }
    }
  }
  object `native-tests` extends NativeTests {
    def cliLauncher = cli.nativeImage
  }
  object `native-static-tests` extends NativeTests {
    def cliLauncher = cli.`static-image`.nativeImage
  }
  object `native-mostly-static-tests` extends NativeTests {
    def cliLauncher = cli.`mostly-static-image`.nativeImage
  }
}

def webScalaVersion = ScalaVersions.scala213
trait Web extends CsScalaJsModule {
  def scalaVersion = webScalaVersion
  // ScalaJSBundlerPlugin
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.js(webScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaJsReact
  )
  def moduleKind = mill.scalajslib.api.ModuleKind.CommonJSModule
  // scalaJSUseMainModuleInitializer := true
  // webpackConfigFile := Some(resourceDirectory.in(Compile).value / "webpack.config.js")
  // npmDependencies.in(Compile) ++= Seq(
  //   "bootstrap" -> "3.3.4",
  //   "bootstrap-treeview" -> "1.2.0",
  //   "graphdracula" -> "1.2.1",
  //   "webpack-raphael" -> "2.1.4",
  //   "react" -> "16.13.1",
  //   "react-dom" -> "16.13.1",
  //   "requirejs" -> "2.3.6",
  //   "sax" -> "1.2.4"
  // )
  // browserifyBundle("sax")
}

object `redirecting-server` extends CsModule {
  def scalaVersion = ScalaVersions.scala212
  def ivyDeps = Agg(
    Deps.http4sBlazeServer,
    Deps.http4sDsl,
    Deps.http4sServer
  )
  def mainClass = Some("redirectingserver.RedirectingServer")
}

def simpleNative03CliTest() = T.command {
  `launcher-native_03`.publishLocal()()
  val launcher = cli.launcher().path
  val tmpDir   = os.temp.dir(prefix = "coursier-bootstrap-scala-native-test")
  def cleanUp(): Unit =
    try os.remove.all(tmpDir)
    catch {
      case _: java.io.IOException =>
        System.err.println(s"Error removing $tmpDir, ignoring it")
    }
  val res =
    try {
      os.proc(
        launcher.toString,
        "bootstrap",
        "-S",
        "-o",
        "native-echo",
        "io.get-coursier:echo_native0.3_2.11:1.0.1"
      ).call(cwd = tmpDir) // TODO inherit all
      os.proc(tmpDir / "native-echo", "-n", "foo", "a").call()
    }
    finally cleanUp()
  assert(res.out.text == "foo a")
}

def simpleNative04CliTest() = T.command {
  `launcher-native_04`.publishLocal()()
  val launcher = cli.launcher().path
  val tmpDir   = os.temp.dir(prefix = "coursier-bootstrap-scala-native-test")
  def cleanUp(): Unit =
    try os.remove.all(tmpDir)
    catch {
      case _: java.io.IOException =>
        System.err.println(s"Error removing $tmpDir, ignoring it")
    }
  val res =
    try {
      os.proc(
        launcher.toString,
        "bootstrap",
        "-S",
        "-o",
        "native-echo",
        "io.get-coursier:echo_native0.4_2.13:1.0.5"
      ).call(cwd = tmpDir) // TODO inherit all
      os.proc(tmpDir / "native-echo", "-n", "foo", "a").call()
    }
    finally cleanUp()
  assert(res.out.text == "foo a")
}
def copyTo(task: mill.main.Tasks[PathRef], dest: os.Path) = T.command {
  if (task.value.length > 1)
    sys.error("Expected a single task")
  val ref = task.value.head()
  os.makeDir.all(dest / os.up)
  os.copy.over(ref.path, dest)
}
def copyLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.nativeImage().path
  ghreleaseassets.copyLauncher(nativeLauncher, os.Path(directory, os.pwd))
}

def copyStaticLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.`static-image`.nativeImage().path
  ghreleaseassets.copyLauncher(nativeLauncher, os.Path(directory, os.pwd), suffix = "-static")
}

def copyMostlyStaticLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.`mostly-static-image`.nativeImage().path
  ghreleaseassets.copyLauncher(
    nativeLauncher,
    os.Path(directory, os.pwd),
    suffix = "-mostly-static"
  )
}

def uploadLaunchers(directory: String = "artifacts") = T.command {
  val version = cli.publishVersion()
  ghreleaseassets.uploadLaunchers(version, os.Path(directory, os.pwd))
}

def bootstrapLauncher(
  version: String = buildVersion,
  dest: String = s"coursier$platformBootstrapExtension"
) = T.command {
  val extraArgs = if (version.endsWith("SNAPSHOT")) Seq("-r", "sonatype:snapshots") else Nil
  cli.run(Seq(
    "bootstrap",
    "-o",
    dest,
    "-f",
    s"$mavenOrg::coursier-cli:$version",
    "--scala",
    cliScalaVersion
  ) ++ extraArgs: _*).map { _ =>
    os.Path(dest, os.pwd)
  }
}

def assemblyLauncher(version: String = buildVersion, dest: String = "coursier.jar") = T.command {
  val extraArgs = if (version.endsWith("SNAPSHOT")) Seq("-r", "sonatype:snapshots") else Nil
  cli.run(Seq(
    "bootstrap",
    "--assembly",
    "-o",
    dest,
    "-f",
    s"$mavenOrg::coursier-cli:$version",
    "--scala",
    cliScalaVersion
  ) ++ extraArgs: _*).map { _ =>
    os.Path(dest, os.pwd)
  }
}

def waitForSync(version: String = buildVersion) = T.command {
  val launcher  = cli.launcher().path
  val extraArgs = if (version.endsWith("SNAPSHOT")) Seq("-r", "sonatype:snapshots") else Nil
  sync.waitForSync(
    launcher.toString,
    s"io.get-coursier:coursier-cli_2.12:$version",
    extraArgs,
    25
  )
}

def copyJarLaunchers(version: String = buildVersion, directory: String = "artifacts") = T.command {
  val bootstrap: os.Path = bootstrapLauncher(version = version)()
  val assembly: os.Path  = assemblyLauncher(version = version)()
  val dir                = os.Path(directory, os.pwd)
  os.copy(
    bootstrap,
    dir / s"coursier$platformBootstrapExtension",
    createFolders = true,
    replaceExisting = true
  )
  os.copy(assembly, dir / "coursier.jar", createFolders = true, replaceExisting = true)
}

def publishSonatype(tasks: mill.main.Tasks[PublishModule.PublishData]) =
  T.command {
    val data = T.sequence(tasks.value)()

    publishing.publishSonatype(
      data = data,
      log = T.ctx().log
    )
  }

private val docScalaVersion = ScalaVersions.scala213
object `doc-deps` extends ScalaModule {
  def scalaVersion = docScalaVersion
  def moduleDeps = Seq(
    coursier.jvm(docScalaVersion),
    interop.cats.jvm(docScalaVersion)
  )
}

object doc extends Doc {
  def scalaVersion = docScalaVersion
  def version      = coursier.jvm(docScalaVersion).publishVersion()
  def classPath    = `doc-deps`.runClasspath()
}

def updateWebsite(dryRun: Boolean = false) = {

  val docusaurusDir       = os.pwd / "doc" / "website"
  val versionedDocsRepo   = "coursier/versioned-docs"
  val versionedDocsBranch = "master"

  val versionOpt =
    Option(System.getenv("GITHUB_REF"))
      .filter(_.startsWith("refs/tags/v"))
      .map(_.stripPrefix("refs/tags/v"))

  System.err.println(s"versionOpt=$versionOpt")

  val token =
    if (dryRun) ""
    else
      Option(System.getenv("GH_TOKEN")).getOrElse {
        sys.error("GH_TOKEN not set")
      }

  T.command {

    doc.copyVersionedData()()
    doc.generate("--npm-install", "--yarn-run-build")()

    for (version <- versionOpt)
      docs.updateVersionedDocs(
        docusaurusDir,
        versionedDocsRepo,
        versionedDocsBranch,
        ghTokenOpt = Some(token),
        newVersion = version,
        dryRun = dryRun,
        cloneUnder = T.dest / "repo"
      )

    // copyDemoFiles()

    docs.updateGhPages(
      docusaurusDir / "build",
      token,
      "coursier/coursier",
      branch = "gh-pages",
      dryRun = dryRun,
      dest = T.dest / "gh-pages"
    )
  }
}

def jvmTests(scalaVersion: String = "*") = {

  def crossTests(sv: String) = Seq(
    // format: off
    core.jvm           .itemMap.get(List(sv)).map(_.test.test()),
    cache.jvm          .itemMap.get(List(sv)).map(_.test.test()),
    env                .itemMap.get(List(sv)).map(_.test.test()),
    coursier.jvm       .itemMap.get(List(sv)).map(_.test.test()),
    tests.jvm          .itemMap.get(List(sv)).map(_.test.test()),
    interop.scalaz.jvm .itemMap.get(List(sv)).map(_.test.test()),
    interop.cats.jvm   .itemMap.get(List(sv)).map(_.test.test()),
    install            .itemMap.get(List(sv)).map(_.test.test()),
    jvm                .itemMap.get(List(sv)).map(_.test.test())
    // format: on
  ).flatten

  def crossIts(sv: String) = Seq(
    // format: off
    coursier.jvm .itemMap.get(List(sv)).map(_.it.test()),
    tests.jvm    .itemMap.get(List(sv)).map(_.it.test())
    // format: on
  ).flatten

  val prerequisites = Seq(
    // required for some tests of `cli-tests`
    `launcher-native_04`.publishLocal()
  )

  val nonCrossTests = Seq(
    // format: off
    `bootstrap-launcher` .test .test(),
    `bootstrap-launcher` .it   .test(),
    cli                  .test .test(),
    `cli-tests`          .test .test()
    // format: on
  )

  val extraTests =
    if (Properties.isWin) Nil
    else
      Seq(
        simpleNative03CliTest(),
        simpleNative04CliTest()
      )

  val scalaVersions =
    if (scalaVersion == "*") ScalaVersions.all
    else Seq(scalaVersion)

  val tasks = prerequisites ++ nonCrossTests ++
    // extraTests ++ // disabled for now (issues on GitHub actions)
    scalaVersions.flatMap { sv =>
      crossTests(sv) ++ crossIts(sv)
    }

  T.command {
    T.sequence(tasks)()

    ()
  }
}

def jsTests(scalaVersion: String = "*") = {

  def crossTests(sv: String) = Seq(
    // format: off
    core.js            .itemMap.get(List(sv)).map(_.test.test()),
    coursier.js        .itemMap.get(List(sv)).map(_.test.test()),
    tests.js           .itemMap.get(List(sv)).map(_.test.test())
    // format: on
  ).flatten

  val scalaVersions =
    if (scalaVersion == "*") ScalaVersions.all
    else Seq(scalaVersion)

  val tasks = scalaVersions.flatMap(sv => crossTests(sv))

  T.command {
    T.sequence(tasks)()
  }
}

def nativeTests() = T.command {
  `cli-tests`.`native-tests`.test()()
}

def nativeStaticTests() = T.command {
  `cli-tests`.`native-static-tests`.test()()
}

def nativeMostlyStaticTests() = T.command {
  `cli-tests`.`native-mostly-static-tests`.test()()
}

object ci extends Module {
  def copyJvm(jvm: String = deps.graalVmJvmId, dest: String = "jvm") = T.command {
    import sys.process._
    val cs = if (Properties.isWin) "cs.exe" else "cs"
    val command = Seq(
      cs,
      "java-home",
      "--jvm",
      jvm,
      "--update",
      "--ttl",
      "0"
    )
    val baseJavaHome = os.Path(command.!!.trim, os.pwd)
    System.err.println(s"Initial Java home $baseJavaHome")
    val destJavaHome = os.Path(dest, os.pwd)
    os.copy(baseJavaHome, destJavaHome, createFolders = true)
    System.err.println(s"New Java home $destJavaHome")
    destJavaHome
  }
}
