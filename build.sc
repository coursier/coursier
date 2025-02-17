import $meta._

import $file.project.deps, deps.{Deps, ScalaVersions, scalaCliVersion}
import $file.project.docs
import $file.project.ghreleaseassets
import $file.project.launchers, launchers.{Launchers, platformBootstrapExtension}
import $file.project.modules.`bootstrap-launcher0`, `bootstrap-launcher0`.BootstrapLauncher
import $file.project.modules.cache0, cache0.{Cache, CacheJvmBase}
import $file.project.modules.core0, core0.{Core, CoreJvmBase}
import $file.project.modules.coursier0, coursier0.{Coursier, CoursierJvmBase, CoursierTests}
import $file.project.modules.doc0, doc0.Doc
import $file.project.modules.interop0, interop0.{Cats, Scalaz}
import $file.project.modules.launcher0, launcher0.LauncherBase
import $file.project.modules.shared, shared.{
  buildVersion,
  CoursierJavaModule,
  CoursierPublishModule,
  CsCrossJvmJsModule,
  CsResourcesTests,
  CsMima,
  CsModule,
  CsScalaJsModule,
  CsTests,
  JsTests,
  JvmTests
}
import $file.project.modules.`sbt-maven-repository0`,
  `sbt-maven-repository0`.{SbtMavenRepository, SbtMavenRepositoryJvmBase}
import $file.project.modules.tests0, tests0.TestsModule
import $file.project.modules.util0, util0.{Util, UtilJvmBase}
import $file.project.publishing, publishing.mavenOrg
import $file.project.relativize, relativize.{relativize => doRelativize}
import $file.project.sync
import $file.project.workers

import _root_.coursier.getcs.GetCs

import mill._
import mill.scalalib._
import mill.scalajslib._
import mill.contrib.bloop.Bloop

import scala.concurrent.duration._
import scala.util.Properties

// Tell mill modules are under modules/
implicit def millModuleBasePath: define.Ctx.BasePath =
  define.Ctx.BasePath(super.millModuleBasePath.value / "modules")

object util extends Module {
  object jvm extends Cross[UtilJvm](ScalaVersions.all)
  object js  extends Cross[UtilJs](ScalaVersions.all)
}
object core extends Module {
  object jvm extends Cross[CoreJvm](ScalaVersions.all)
  object js  extends Cross[CoreJs](ScalaVersions.all)
}
object `sbt-maven-repository` extends Module {
  object jvm extends Cross[SbtMavenRepositoryJvm](ScalaVersions.all)
  object js  extends Cross[SbtMavenRepositoryJs](ScalaVersions.all)
}
object `cache-util` extends CacheUtil
object cache extends Module {
  object jvm extends Cross[CacheJvm](ScalaVersions.all)
  object js  extends Cross[CacheJs](ScalaVersions.all)
}
object launcher             extends Cross[Launcher](ScalaVersions.all)
object env                  extends Cross[Env](ScalaVersions.all)
object `launcher-native_04` extends LauncherNative04

object coursier extends Module {
  object jvm extends Cross[CoursierJvm](ScalaVersions.all)
  object js  extends Cross[CoursierJs](ScalaVersions.all)
}

object `proxy-setup` extends JavaModule with CoursierPublishModule {
  def artifactName = "coursier-proxy-setup"
}

object paths extends CoursierJavaModule {
  def ivyDeps = Agg(
    Deps.directories,
    Deps.isTerminal,
    Deps.jniUtils
  )
}

object `custom-protocol-for-test` extends CsModule {
  def scalaVersion = ScalaVersions.scala213
}

object `bootstrap-launcher` extends BootstrapLauncher { self =>
  def moduleDeps = Seq(
    paths
  )
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
  def windowsAnsiPsSources = T {
    val jars = resolveDeps(
      T.task {
        Agg(Deps.windowsAnsiPs.exclude("*" -> "*"))
          .map(bindDependency())
      },
      sources = true,
      artifactTypes = None
    )()
    jars.foreach { jar =>
      mill.api.IO.unpackZip(jar.path, os.rel)
    }
    Seq(PathRef(T.dest))
  }
  def sources = T {
    super.sources() ++
      proxySources() ++
      windowsAnsiPsSources()
  }
  def resources = T.sources {
    super.resources().flatMap { ref =>
      val dir = ref.path
      if (os.exists(dir) && os.isDir(dir)) {
        val nonIgnoredFiles = os.walk(dir)
          .filter(os.isFile(_))
          .map(_.relativeTo(dir))
          .filter(!_.startsWith(os.sub / "META-INF" / "native-image"))
        if (nonIgnoredFiles.isEmpty) Nil
        else
          sys.error(s"Resource directory $dir contains unexpected resources $nonIgnoredFiles")
      }
      else
        Seq(ref)
    }
  }
  def proguardClassPath = T {
    proguard.runClasspath()
  }
  object test extends SbtTests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Seq(
      Deps.collectionCompat,
      Deps.java8Compat
    )
  }
  object it extends SbtTests with CsTests {
    def sources = T.sources(
      millSourcePath / "src" / "it" / "scala",
      millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = Seq(
      self.test
    )
    def forkArgs = T.input {
      val testRepoServer0 = buildWorkers.testRepoServer()
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
  object jvm extends Cross[TestsJvm](ScalaVersions.all)
  object js  extends Cross[TestsJs](ScalaVersions.all)
}

object `proxy-tests` extends Cross[ProxyTests](ScalaVersions.all)

object interop extends Module {
  object scalaz extends Module {
    object jvm extends Cross[ScalazJvm](ScalaVersions.all)
    object js  extends Cross[ScalazJs](ScalaVersions.all)
  }
  object cats extends Module {
    object jvm extends Cross[CatsJvm](ScalaVersions.all)
    object js  extends Cross[CatsJs](ScalaVersions.all)
  }
}

object jvm     extends Cross[Jvm](ScalaVersions.all)
object install extends Cross[Install](ScalaVersions.all)

object cli extends Cli {
  object test extends SbtTests with CsTests with CsResourcesTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.jvm(cliScalaVersion213Compat).test
    )
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.ujson
    )
  }
}
object `cli-tests` extends CliTests

object web extends Web

object `test-cache` extends Module {
  object jvm extends Cross[TestCacheJvm](ScalaVersions.all)
  object js  extends Cross[TestCacheJs](ScalaVersions.all)
}

trait UtilJvm extends UtilJvmBase {
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.jsoup
  )
}
trait UtilJs extends CsScalaJsModule with Util

trait CoreJvm extends CoreJvmBase {
  def moduleDeps = super.moduleDeps ++ Seq(
    util.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.concurrentReferenceHashMap,
    Deps.scalaXml
  )
  def commitHash = `build-util`.commitHash
  object test extends CrossSbtTests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.jol
    )
  }
}
trait CoreJs extends Core with CsScalaJsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    util.js()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaJsDom
  )
  def commitHash = `build-util`.commitHash
  object test extends SbtTests with ScalaJSTests with JsTests with CsTests
}

trait SbtMavenRepositoryJvm extends SbtMavenRepositoryJvmBase {
  def moduleDeps = super.moduleDeps ++ Seq(
    core.jvm()
  )
}
trait SbtMavenRepositoryJs extends SbtMavenRepository
    with CsScalaJsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    core.js()
  )
}

trait CacheUtil extends CoursierPublishModule with CsMima {
  def mimaPreviousVersions = T {
    import _root_.coursier.core.Version
    val cutOff = Version("2.1.15")
    super.mimaPreviousVersions()
      .map(Version(_))
      .filter(_ >= cutOff)
      .map(_.repr)
  }
  // Remove once 2.1.15 is out
  def mimaPreviousArtifacts = T {
    val versions     = mimaPreviousVersions()
    val organization = pomSettings().organization
    val artifactId0  = artifactId()
    Agg.from(
      versions.map(version => ivy"$organization:$artifactId0:$version")
    )
  }
}

trait CacheJvm extends CacheJvmBase {
  def moduleDeps = Seq(
    `cache-util`,
    util.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.directories,
    Deps.isTerminal,
    Deps.jniUtils,
    Deps.plexusArchiver,
    Deps.plexusContainerDefault,
    Deps.scalaCliConfig,
    Deps.tika,
    Deps.windowsAnsi
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.jsoniterMacros,
    Deps.svm
  )
  def sources = T.sources {
    super.sources() ++ paths.sources()
  }
  def customLoaderCp = T {
    `custom-protocol-for-test`.runClasspath()
  }
  object test extends CacheJvmBaseTests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.http4sBlazeServer,
      Deps.http4sDsl,
      Deps.logbackClassic,
      Deps.osLib,
      Deps.pprint,
      Deps.scalaAsync
    )
    def compileIvyDeps = super.compileIvyDeps() ++ Agg(
      Deps.jsoniterMacros
    )
    def forkEnv = super.forkEnv() ++ Seq(
      "COURSIER_CUSTOMPROTOCOL_BASE" -> T.workspace.toString
    )
  }
}
trait CacheJs extends Cache with CsScalaJsModule {
  def moduleDeps = Seq(
    util.js()
  )
  def artifactName = "fetch-js"
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaJsDom
  )
}

trait Launcher extends LauncherBase {
  def ivyDeps = super.ivyDeps() ++ Seq(
    Deps.collectionCompat,
    Deps.noCrcZis,
    Deps.pythonNativeLibs
  )
  def compileIvyDeps = Agg(
    Deps.dataClass
  )
  def commitHash = `build-util`.commitHash

  def bootstrap                   = `bootstrap-launcher`.proguardedAssembly()
  def resourceBootstrap           = `bootstrap-launcher`.proguardedResourceAssembly()
  def noProguardBootstrap         = `bootstrap-launcher`.assembly()
  def noProguardResourceBootstrap = `bootstrap-launcher`.resourceAssembly()
}

trait Env extends CrossSbtModule with CsModule
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
  object test extends CrossSbtTests with CsTests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.jimfs
    )
  }
}

def cliScalaVersion          = ScalaVersions.scala3
def cliScalaVersion213Compat = ScalaVersions.scala213
def launcherModule           = launcher
trait LauncherNative04 extends CsModule
    with CoursierPublishModule {
  def scalaVersion = cliScalaVersion
  def artifactName = "coursier-launcher-native_0.4"
  def compileModuleDeps = Seq(
    launcherModule(cliScalaVersion213Compat)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalaNativeTools040
  )
}

trait CoursierJvm extends CoursierJvmBase { self =>
  def moduleDeps = Seq(
    core.jvm(),
    cache.jvm(),
    `proxy-setup`
  )
  // Put CoursierTests right after TestModule, and see what happens
  object test extends TestModule with CrossSbtTests with CoursierTests with CsTests
      with JvmTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      `test-cache`.jvm()
    )
  }
  object it extends TestModule with CrossSbtTests with CoursierTests with CsTests
      with JvmTests {
    def sources = T.sources(
      this.millSourcePath / "src" / "it" / "scala",
      this.millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = super.moduleDeps ++ Seq(
      self.test
    )
    def forkArgs = T.input {
      val testRepoServer0 = buildWorkers.testRepoServer()
      super.forkArgs() ++ Seq(
        s"-Dtest.repository=${testRepoServer0.url}",
        s"-Dtest.repository.user=${testRepoServer0.user}",
        s"-Dtest.repository.password=${testRepoServer0.password}"
      )
    }
  }
}
trait CoursierJs extends Coursier with CsScalaJsModule {
  def moduleDeps = Seq(
    core.js(),
    cache.js()
  )
  object test extends SbtTests with ScalaJSTests with CsTests with JsTests with CoursierTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      `test-cache`.js()
    )
  }
}

trait TestsJvm extends TestsModule { self =>
  def moduleDeps = super.moduleDeps ++ Seq(
    core.jvm(),
    `sbt-maven-repository`.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.jsoup
  )
  object test extends CrossSbtTests with CsTests with JvmTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.jvm(),
      `test-cache`.jvm()
    )
  }
  object it extends CrossSbtTests with CsTests with JvmTests
      with workers.UsesRedirectingServer {
    def redirectingServerCp =
      `redirecting-server`.runClasspath()
    def redirectingServerMainClass =
      `redirecting-server`.mainClass().getOrElse(sys.error("no main class"))
    def forkArgs = T.input {
      val redirectingServer0 = redirectingServer()
      val testRepoServer0    = buildWorkers.testRepoServer()
      super.forkArgs() ++ Seq(
        s"-Dtest.redirect.repository=${redirectingServer0.url}",
        s"-Dtest.repository=${testRepoServer0.url}",
        s"-Dtest.repository.user=${testRepoServer0.user}",
        s"-Dtest.repository.password=${testRepoServer0.password}"
      )
    }
    def sources = T.sources(
      this.millSourcePath / "src" / "it" / "scala",
      this.millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.jvm(),
      self.test
    )
  }
}
trait TestsJs extends TestsModule with CsScalaJsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    core.js(),
    `sbt-maven-repository`.js()
  )
  // testOptions := testOptions.dependsOn(runNpmInstallIfNeeded).value
  object test extends SbtTests with ScalaJSTests with CsTests with JsTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      coursier.js(),
      `test-cache`.js()
    )
  }
}

trait ProxyTests extends CrossSbtModule with CsModule {
  def moduleDeps = super.moduleDeps ++ Seq(
    `proxy-setup`
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.dockerClient,
    Deps.scalaAsync,
    Deps.slf4JNop
  )
  object it extends CrossSbtTests with CsTests {
    def sources = T.sources(
      millSourcePath / "src" / "it" / "scala",
      millSourcePath / "src" / "it" / "java"
    )
    def moduleDeps = super.moduleDeps ++ Seq(
      tests.jvm().test
    )
    def testFramework = "coursier.tests.CustomFramework"
  }
  // sharedTestResources
}

trait ScalazJvm extends Scalaz with CsMima {
  def moduleDeps = Seq(
    cache.jvm()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalazConcurrent
  )
  object test extends CrossSbtTests with CsTests with CsResourcesTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      tests.jvm().test
    )
  }
}
trait ScalazJs extends Scalaz with CsScalaJsModule {
  def moduleDeps = Seq(
    cache.js()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.scalazCore
  )
}

trait CatsJvm extends Cats with CsMima {
  def moduleDeps = Seq(
    cache.jvm()
  )
  object test extends CrossSbtTests with CsTests with CsResourcesTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      tests.jvm().test
    )
  }
}
trait CatsJs extends Cats with CsScalaJsModule {
  def moduleDeps = Seq(
    cache.js()
  )
}

trait Install extends CrossSbtModule with CsModule
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
  object test extends CrossSbtTests with CsTests with CsResourcesTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      `test-cache`.jvm()
    )
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.pprint
    )
  }
}

trait Jvm extends CrossSbtModule with CsModule
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
    Deps.jna,
    Deps.jsoniterCore
  )
  object test extends CrossSbtTests with CsTests with CsResourcesTests {
    def moduleDeps = super.moduleDeps ++ Seq(
      `test-cache`.jvm()
    )
    def ivyDeps = super.ivyDeps() ++ Seq(
      Deps.osLib,
      Deps.pprint,
      Deps.scalaAsync
    )
    def mockCache = T.source {
      PathRef(T.workspace / "modules" / "jvm" / "src" / "test" / "resources" / "mock-cache")
    }
    def forkEnv = super.forkEnv() ++ Seq(
      "COURSIER_JVM_TESTS_MOCK_CACHE" -> mockCache().path.toString
    )
  }
}

trait Cli extends CsModule
    with CoursierPublishModule with Launchers {
  def scalaVersion = cliScalaVersion
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.jvm(cliScalaVersion213Compat),
    `sbt-maven-repository`.jvm(cliScalaVersion213Compat),
    install(cliScalaVersion213Compat),
    jvm(cliScalaVersion213Compat),
    launcherModule(cliScalaVersion213Compat)
  )
  def artifactName = "coursier-cli"
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.caseApp,
    Deps.catsFree213,
    Deps.classPathUtil,
    Deps.collectionCompat,
    Deps.noCrcZis,
    Deps.slf4JNop
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.jsoniterMacros,
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
      Name.IMPLEMENTATION_TITLE.toString   -> "coursier-cli",
      Name.IMPLEMENTATION_VERSION.toString -> ver,
      Name.SPECIFICATION_VENDOR.toString   -> "io.get-coursier",
      Name.SPECIFICATION_TITLE.toString    -> "coursier-cli",
      "Implementation-Vendor-Id"           -> "io.get-coursier",
      Name.SPECIFICATION_VERSION.toString  -> ver,
      Name.IMPLEMENTATION_VENDOR.toString  -> "io.get-coursier"
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
}

trait CliTests extends CsModule
    with CoursierPublishModule { self =>
  def scalaVersion = cliScalaVersion
  def moduleDeps = super.moduleDeps ++ Seq(
    coursier.jvm(cliScalaVersion213Compat)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.caseApp,
    Deps.dockerClient,
    Deps.osLib,
    Deps.pprint,
    Deps.ujson,
    Deps.utest
  )
  private def sharedTestArgs = Seq(
    s"-Dcoursier-test.scala-cli=${GetCs.scalaCli(scalaCliVersion)}"
  )
  def docJar = Task {
    // dottydoc currently crashes when generating this module's doc jar
    emptyZip()
  }
  object test extends SbtTests with CsTests {
    def forkArgs = {
      val launcherTask = cli.launcher.map(_.path)
      val assemblyTask = cli.assembly.map(_.path)
      T {
        super.forkArgs() ++ sharedTestArgs ++ Seq(
          s"-Dcoursier-test-launcher=${launcherTask()}",
          s"-Dcoursier-test-assembly=${assemblyTask()}",
          "-Dcoursier-test-launcher-accepts-D=false",
          "-Dcoursier-test-launcher-accepts-J=true",
          "-Dcoursier-test-is-native=false",
          "-Dcoursier-test-is-native-static=false",
          s"-Dcoursier.test.auth-proxy-data-dir=${T.workspace / "project/authenticated-proxy"}",
          s"-Dcoursier.test.auth-proxy-image=${deps.Docker.alpineImage}",
          s"-Dcoursier.test.alpine-image=${deps.Docker.alpineImage}",
          s"-Dcoursier.test.alpine-java-image=${deps.Docker.alpineJavaImage}"
        )
      }
    }
  }
  trait NativeTests extends SbtTests with CsTests with Bloop.Module {
    def cliLauncher: T[PathRef]
    def skipBloop = true
    def sources = T.sources {
      super.sources() ++ self.test.sources()
    }
    def isStatic: Boolean = false
    def forkArgs = {
      val launcherTask = cliLauncher.map(_.path)
      T {
        val launcher = launcherTask()
        super.forkArgs() ++ sharedTestArgs ++ Seq(
          s"-Dcoursier-test-launcher=$launcher",
          s"-Dcoursier-test-assembly=$launcher",
          "-Dcoursier-test-launcher-accepts-D=false",
          "-Dcoursier-test-launcher-accepts-J=false",
          "-Dcoursier-test-is-native=true",
          s"-Dcoursier-test-is-native-static=$isStatic",
          s"-Dcoursier.test.auth-proxy-data-dir=${T.workspace / "project/authenticated-proxy"}",
          s"-Dcoursier.test.auth-proxy-image=${deps.Docker.alpineImage}",
          s"-Dcoursier.test.alpine-image=${deps.Docker.alpineImage}",
          s"-Dcoursier.test.alpine-java-image=${deps.Docker.alpineJavaImage}"
        )
      }
    }
  }
  object `native-tests` extends NativeTests {
    def cliLauncher = cli.nativeImage
  }
  object `native-static-tests` extends NativeTests {
    def cliLauncher = cli.`static-image`.nativeImage
    def isStatic    = true
  }
  object `native-mostly-static-tests` extends NativeTests {
    def cliLauncher = cli.`mostly-static-image`.nativeImage
  }
  object `native-container-tests` extends NativeTests {
    def cliLauncher = cli.containerImage
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
  def scalaVersion = ScalaVersions.scala213
  def ivyDeps = Agg(
    Deps.http4sBlazeServer,
    Deps.http4sDsl,
    Deps.http4sServer,
    Deps.isTerminal
  )
  def mainClass = Some("redirectingserver.RedirectingServer")
}

trait TestCacheJvm extends Cross.Module[String] with CsCrossJvmJsModule {
  def scalaVersion = crossValue
  def moduleDeps = Seq(
    cache.jvm()
  )
}
trait TestCacheJs extends Cross.Module[String] with CsCrossJvmJsModule with CsScalaJsModule {
  def scalaVersion = crossValue
  def moduleDeps = Seq(
    cache.js()
  )
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
  assert(res.out.text() == "foo a")
}

def copyTo(task: mill.main.Tasks[PathRef], dest: String) = T.command {
  if (task.value.length > 1)
    sys.error("Expected a single task")
  val ref   = task.value.head()
  val dest1 = os.Path(dest, T.workspace)
  os.makeDir.all(dest1 / os.up)
  os.copy.over(ref.path, dest1)
}
def copyLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.nativeImage().path
  ghreleaseassets.copyLauncher(nativeLauncher, os.Path(directory, T.workspace))
}

def copyStaticLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.`static-image`.nativeImage().path
  ghreleaseassets.copyLauncher(nativeLauncher, os.Path(directory, T.workspace), suffix = "-static")
}

def copyMostlyStaticLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.`mostly-static-image`.nativeImage().path
  ghreleaseassets.copyLauncher(
    nativeLauncher,
    os.Path(directory, T.workspace),
    suffix = "-mostly-static"
  )
}

def copyContainerLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.containerImage().path
  ghreleaseassets.copyLauncher(
    nativeLauncher,
    os.Path(directory, T.workspace),
    suffix = "-container"
  )
}

def uploadLaunchers(directory: String = "artifacts") = T.command {
  val version = cli.publishVersion()
  ghreleaseassets.uploadLaunchers(version, os.Path(directory, T.workspace))
}

def bootstrapLauncher(
  version: String = buildVersion,
  dest: String = s"coursier$platformBootstrapExtension"
) = T.command {
  cli.run(T.task {
    val extraArgs = if (version.endsWith("SNAPSHOT")) Seq("-r", "sonatype:snapshots") else Nil
    Args(Seq(
      "bootstrap",
      "-o",
      dest,
      "-f",
      s"$mavenOrg::coursier-cli:$version",
      "--scala",
      cliScalaVersion
    ) ++ extraArgs)
  })()
  os.Path(dest, T.workspace)
}

def assemblyLauncher(version: String = buildVersion, dest: String = "coursier.jar") = T.command {
  cli.run(T.task {
    val extraArgs = if (version.endsWith("SNAPSHOT")) Seq("-r", "sonatype:snapshots") else Nil
    Args(
      Seq(
        "bootstrap",
        "--assembly",
        "-o",
        dest,
        "-f",
        s"$mavenOrg::coursier-cli:$version",
        "--scala",
        cliScalaVersion
      ) ++ extraArgs
    )
  })()
  os.Path(dest, T.workspace)
}

def waitForSync(version: String = buildVersion) = T.command {
  val launcher  = cli.launcher().path
  val extraArgs = if (version.endsWith("SNAPSHOT")) Seq("-r", "sonatype:snapshots") else Nil
  sync.waitForSync(
    launcher.toString,
    s"io.get-coursier:coursier-cli_2.13:$version",
    extraArgs,
    25
  )
}

def copyJarLaunchers(version: String = buildVersion, directory: String = "artifacts") = T.command {
  val bootstrap: os.Path = bootstrapLauncher(version = version)()
  val assembly: os.Path  = assemblyLauncher(version = version)()
  val dir                = os.Path(directory, T.workspace)
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
      log = T.ctx().log,
      workspace = T.workspace
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

def updateWebsite(rootDir: String = "", dryRun: Boolean = false) = T.command {

  val rootDir0            = if (rootDir.isEmpty) T.workspace else os.Path(rootDir, T.workspace)
  val docusaurusDir       = rootDir0 / "doc" / "website"
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

def jvmTests(scalaVersion: String = ScalaVersions.scala213) = {

  def crossTests(sv: String) = Seq(
    // format: off
    core.jvm           .valuesToModules.get(List(sv)).map(_.test.test()),
    cache.jvm          .valuesToModules.get(List(sv)).map(_.test.test()),
    env                .valuesToModules.get(List(sv)).map(_.test.test()),
    coursier.jvm       .valuesToModules.get(List(sv)).map(_.test.test()),
    tests.jvm          .valuesToModules.get(List(sv)).map(_.test.test()),
    interop.scalaz.jvm .valuesToModules.get(List(sv)).map(_.test.test()),
    interop.cats.jvm   .valuesToModules.get(List(sv)).map(_.test.test()),
    install            .valuesToModules.get(List(sv)).map(_.test.test()),
    jvm                .valuesToModules.get(List(sv)).map(_.test.test())
    // format: on
  ).flatten

  def crossIts(sv: String) = Seq(
    // format: off
    coursier.jvm .valuesToModules.get(List(sv)).map(_.it.test()),
    tests.jvm    .valuesToModules.get(List(sv)).map(_.it.test())
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
    core.js            .valuesToModules.get(List(sv)).map(_.test.test()),
    coursier.js        .valuesToModules.get(List(sv)).map(_.test.test()),
    tests.js           .valuesToModules.get(List(sv)).map(_.test.test())
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

def nativeContainerTests() = T.command {
  `cli-tests`.`native-container-tests`.test()()
}

def cliNativeImageLauncher() = T.command {
  cli.nativeImage()
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
    val baseJavaHome = os.Path(command.!!.trim, T.workspace)
    System.err.println(s"Initial Java home $baseJavaHome")
    val destJavaHome = os.Path(dest, T.workspace)
    os.copy(baseJavaHome, destJavaHome, createFolders = true)
    System.err.println(s"New Java home $destJavaHome")
    destJavaHome
  }
}

object buildWorkers extends Module {

  def testRepoServer = T.worker {
    val server = new workers.TestRepoServer

    if (server.healthCheck())
      sys.error("Test repo server already running")

    server.proc = os.proc(
      "cs",
      "launch",
      "io.get-coursier:http-server_2.12:1.0.0",
      "--",
      "-d",
      "modules/tests/handmade-metadata/data/http/abc.com",
      "-u",
      server.user,
      "-P",
      server.password,
      "-r",
      "realm",
      "-v",
      "--host",
      server.host,
      "--port",
      server.port.toString
    ).spawn(
      cwd = T.workspace,
      stdin = os.Pipe,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    server.proc.stdin.close()
    var serverRunning = false
    var countDown     = 20
    while (!serverRunning && server.proc.isAlive() && countDown > 0) {
      serverRunning = server.healthCheck()
      if (!serverRunning)
        Thread.sleep(500L)
      countDown -= 1
    }
    if (serverRunning && server.proc.isAlive()) {
      T.log.outputStream.println(s"Test repository listening on ${server.url}")
      server
    }
    else
      sys.error("Cannot run test repo server")
  }

}

object `build-util` extends Module {

  def commitHash = T {
    os.proc("git", "rev-parse", "HEAD").call().out.text().trim()
  }

}
