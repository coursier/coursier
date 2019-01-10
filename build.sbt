
import Aliases._
import Settings.{crossProject, project, _}
import Publish._

lazy val core = crossProject("core")(JSPlatform, JVMPlatform)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading,
    libs ++= Seq(
      Deps.fastParse % "shaded",
      Deps.jsoup % "shaded",
      Deps.scalaXml
    ),
    shadeNamespaces ++= Set(
      "org.jsoup",
      "fastparse",
      "sourcecode"
    ),
    generatePropertyFile
  )
  .jsSettings(
    libs ++= Seq(
      CrossDeps.fastParse.value,
      CrossDeps.scalaJsDom.value
    )
  )
  .settings(
    shared,
    coursierPrefix,
    dontPublishScalaJsIn("2.11"),
    libs += Deps.scalaReflect.value % Provided,
    Mima.previousArtifacts,
    Mima.coreFilters
  )

lazy val coreJvm = core.jvm
lazy val coreJs = core.js

lazy val tests = crossProject("tests")(JSPlatform, JVMPlatform)
  .dependsOn(core, cache % Test)
  .jsSettings(
    scalaJSStage.in(Global) := FastOptStage,
    testOptions := testOptions.dependsOn(runNpmInstallIfNeeded).value
  )
  .configs(Integration)
  .settings(
    shared,
    dontPublish,
    hasITs,
    coursierPrefix,
    libs += Deps.scalaAsync,
    utest,
    sharedTestResources
  )

lazy val testsJvm = tests.jvm
lazy val testsJs = tests.js

lazy val `proxy-tests` = project("proxy-tests")
  .dependsOn(testsJvm % "test->test")
  .configs(Integration)
  .settings(
    shared,
    dontPublish,
    hasITs,
    coursierPrefix,
    libs ++= Seq(
      Deps.dockerClient,
      Deps.scalaAsync,
      Deps.slf4JNop
    ),
    utest,
    sharedTestResources
  )

lazy val paths = project("paths")
  .settings(
    pureJava,
    dontPublish,
    addDirectoriesSources
  )

lazy val cache = crossProject("cache")(JSPlatform, JVMPlatform)
  .dependsOn(core)
  .jvmSettings(
    addPathsSources
  )
  .jsSettings(
    name := "fetch-js"
  )
  .settings(
    shared,
    dontPublishScalaJsIn("2.11"),
    Mima.previousArtifacts,
    coursierPrefix,
    Mima.cacheFilters
  )

lazy val cacheJvm = cache.jvm
lazy val cacheJs = cache.js

lazy val scalaz = crossProject("interop", "scalaz")(JSPlatform, JVMPlatform)
  .dependsOn(cache, tests % "test->test")
  .jvmSettings(
    libs += Deps.scalazConcurrent
  )
  .jsSettings(
    libs += CrossDeps.scalazCore.value
  )
  .settings(
    name := "scalaz-interop",
    shared,
    dontPublishScalaJsIn("2.11"),
    utest,
    Mima.previousArtifacts,
    coursierPrefix
  )

lazy val scalazJvm = scalaz.jvm
lazy val scalazJs = scalaz.js

lazy val cats = crossProject("interop", "cats")(JSPlatform, JVMPlatform)
  .dependsOn(cache, tests % "test->test")
  .settings(
    name := "cats-interop",
    shared,
    dontPublishScalaJsIn("2.11"),
    utest,
    Mima.previousArtifacts,
    coursierPrefix,
    libs += CrossDeps.catsEffect.value
  )

lazy val catsJvm = cats.jvm
lazy val catsJs = cats.js

lazy val `bootstrap-launcher` = project("bootstrap-launcher")
  .enablePlugins(SbtProguard)
  .settings(
    pureJava,
    dontPublish,
    addPathsSources,
    // seems not to be automatically found with sbt 0.13.16-M1 :-/
    mainClass := Some("coursier.Bootstrap"),
    proguardedBootstrap
  )

lazy val bootstrap = project("bootstrap")
  .settings(
    shared,
    coursierPrefix
  )

lazy val extra = project("extra")
  .enablePlugins(ShadingPlugin)
  .dependsOn(coreJvm, cacheJvm)
  .settings(
    shared,
    coursierPrefix,
    shading,
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.scalaNativeTools % "shaded",
          // Still applies?
          //   brought by only tools, so should be automatically shaded,
	        //   but issues in ShadingPlugin (with things published locally?)
	        //   seem to require explicit shading...
          Deps.scalaNativeNir % "shaded",
          Deps.scalaNativeUtil % "shaded",
          Deps.fastParse % "shaded"
        )
      else
        Nil
    },
    shadeNamespaces ++=
      Set(
        "fastparse",
        "sourcecode"
      ) ++
      // not blindly shading the whole scala.scalanative here, for some constant strings starting with
      // "scala.scalanative.native." in scalanative not to get prefixed with "coursier.shaded."
      Seq("codegen", "io", "linker", "nir", "optimizer", "tools", "util")
        .map("scala.scalanative." + _)
  )

lazy val cli = project("cli")
  .dependsOn(bootstrap, coreJvm, cacheJvm, extra)
  .enablePlugins(PackPlugin, SbtProguard)
  .settings(
    shared,
    dontPublishIn("2.11"),
    coursierPrefix,
    unmanagedResources.in(Test) += proguardedJar.in(`bootstrap-launcher`).in(Compile).value,
    scalacOptions += "-Ypartial-unification",
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.caseApp,
          Deps.catsCore,
          Deps.argonautShapeless,
          Deps.junit % Test, // to be able to run tests with pants
          Deps.scalatest % Test
        )
      else
        Seq()
    },
    mainClass.in(Compile) := {
      if (scalaBinaryVersion.value == "2.12")
        Some("coursier.cli.Coursier")
      else
        None
    },
    addBootstrapJarAsResource,
    proguardedCli
  )

lazy val web = project("web")
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(coreJs, cacheJs)
  .settings(
    shared,
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          CrossDeps.scalaJsJquery.value,
          CrossDeps.scalaJsReact.value
        )
      else
        Seq()
    },
    sourceDirectory := {
      val dir = sourceDirectory.value

      if (scalaBinaryVersion.value == "2.12")
        dir
      else
        dir / "target" / "dummy"
    },
    noTests,
    webjarBintrayRepository,
    scalaJSUseMainModuleInitializer := true,
    webpackConfigFile := Some(resourceDirectory.in(Compile).value / "webpack.config.js"),
    npmDependencies.in(Compile) ++= Seq(
      "bootstrap" -> "3.3.4",
      "bootstrap-treeview" -> "1.2.0",
      "graphdracula" -> "1.2.1",
      "webpack-raphael" -> "2.1.4",
      "react" -> "15.6.1",
      "react-dom" -> "15.6.1",
      "requirejs" -> "2.3.6"
    )
  )

lazy val okhttp = project("okhttp")
  .dependsOn(cacheJvm)
  .settings(
    shared,
    coursierPrefix,
    libs += Deps.okhttpUrlConnection
  )

lazy val meta = crossProject("meta")(JSPlatform, JVMPlatform)
  .dependsOn(core, cache)
  .settings(
    shared,
    dontPublishScalaJsIn("2.11"),
    moduleName := "coursier",
    // POM only
    publishArtifact.in(Compile, packageDoc) := false,
    publishArtifact.in(Compile, packageSrc) := false,
    publishArtifact.in(Compile, packageBin) := false
  )

lazy val metaJvm = meta.jvm
lazy val metaJs = meta.js

lazy val jvm = project("jvm")
  .dummy
  .aggregate(
    coreJvm,
    testsJvm,
    `proxy-tests`,
    paths,
    cacheJvm,
    scalazJvm,
    catsJvm,
    `bootstrap-launcher`,
    bootstrap,
    extra,
    cli,
    okhttp,
    metaJvm
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-jvm"
  )

lazy val js = project("js")
  .dummy
  .aggregate(
    coreJs,
    cacheJs,
    testsJs,
    web,
    metaJs
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-js"
  )

lazy val coursier = project("coursier")
  .in(root)
  .aggregate(
    catsJvm,
    catsJs,
    coreJvm,
    coreJs,
    testsJvm,
    testsJs,
    `proxy-tests`,
    paths,
    cacheJvm,
    cacheJs,
    `bootstrap-launcher`,
    bootstrap,
    extra,
    cli,
    scalazJvm,
    scalazJs,
    web,
    okhttp,
    metaJvm,
    metaJs
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-root"
  )


lazy val addBootstrapJarAsResource = {

  import java.nio.file.Files

  packageBin.in(Compile) := {
    val originalBootstrapJar = packageBin.in(`bootstrap-launcher`).in(Compile).value
    val bootstrapJar = proguardedJar.in(`bootstrap-launcher`).in(Compile).value
    val source = packageBin.in(Compile).value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

    ZipUtil.addToZip(source, dest, Seq(
      "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
      "bootstrap-orig.jar" -> Files.readAllBytes(originalBootstrapJar.toPath)
    ))

    dest
  }
}

lazy val proguardedJarWithBootstrap = Def.task {

  import java.nio.file.Files

  val bootstrapJar = proguardedJar.in(`bootstrap-launcher`).in(Compile).value
  val origBootstrapJar = packageBin.in(`bootstrap-launcher`).in(Compile).value
  val source = proguardedJar.value

  val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")
  val dest0 = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap-and-prelude.jar")

  ZipUtil.addToZip(source, dest, Seq(
    "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
    "bootstrap-orig.jar" -> Files.readAllBytes(origBootstrapJar.toPath)
  ))

  ZipUtil.addPrelude(dest, dest0)

  dest0
}

lazy val proguardedBootstrap = Seq(
  proguardedJar := proguardedJarTask.value,
  proguardVersion.in(Proguard) := SharedVersions.proguard,
  proguardOptions.in(Proguard) ++= Seq(
    "-dontwarn",
    "-repackageclasses coursier.bootstrap.launcher",
    "-keep class coursier.bootstrap.launcher.Bootstrap {\n  public static void main(java.lang.String[]);\n}",
    "-keep class coursier.bootstrap.launcher.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}"
  ),
  javaOptions.in(Proguard, proguard) := Seq("-Xmx3172M"),
  artifactPath.in(Proguard) := proguardDirectory.in(Proguard).value / "bootstrap.jar"
)

lazy val proguardedCli = Seq(
  proguardedJar := Def.taskDyn {
    val dummy = sys.env.get("DUMMY_PROGUARD").filter(_ != "0") match {
      case Some("1") => true
      case Some(s) => sys.error(s"Invalid DUMMY_PROGUARD value: '$s'")
      case None => false
    }
    if (dummy)
      Def.task {
        java.nio.file.Files.createTempFile("dummy-proguard-cli", ".jar")
          .toFile
      }
    else
      proguardedJarTask
  }.value,
  proguardVersion.in(Proguard) := SharedVersions.proguard,
  proguardOptions.in(Proguard) ++= Seq(
    "-dontwarn",
    "-dontnote",
    "-dontoptimize", // required since the switch to scala 2.12
    "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
    "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}",
    "-adaptresourcefilenames **.properties",
    // keeping only scala.Symbol doesn't seem to be enough since the switch to proguard 6.0.x
    """-keep class scala.** { *; }"""
  ),
  javaOptions.in(Proguard, proguard) := Seq("-Xmx3172M"),
  artifactPath.in(Proguard) := proguardDirectory.in(Proguard).value / "coursier-standalone.jar",
  artifacts ++= {
    if (scalaBinaryVersion.value == "2.12")
      Seq(proguardedArtifact.value)
    else
      Nil
  },
  addProguardedJar
)

lazy val addProguardedJar = {

  val extra = Def.taskDyn[Map[Artifact, File]] {
    if (scalaBinaryVersion.value == "2.12")
      Def.task(Map(proguardedArtifact.value -> proguardedJarWithBootstrap.value))
    else
      Def.task(Map())
  }

  packagedArtifacts ++= extra.value
}

lazy val sharedTestResources = {
  unmanagedResourceDirectories.in(Test) ++= {
    val baseDir = baseDirectory.in(LocalRootProject).value
    val testsMetadataDir = baseDir / "modules" / "tests" / "metadata" / "https"
    if (!testsMetadataDir.exists())
      gitLock.synchronized {
        if (!testsMetadataDir.exists()) {
          val cmd = Seq("git", "submodule", "update", "--init", "--recursive", "--", "modules/tests/metadata")
          runCommand(cmd, baseDir)
        }
      }
    val testsHandmadeMetadataDir = baseDir / "modules" / "tests" / "handmade-metadata" / "data"
    if (!testsHandmadeMetadataDir.exists())
      gitLock.synchronized {
        if (!testsHandmadeMetadataDir.exists()) {
          val cmd = Seq("git", "submodule", "update", "--init", "--recursive", "--", "modules/tests/handmade-metadata")
          runCommand(cmd, baseDir)
        }
      }
    Nil
  }
}

// Using directly the sources of directories, rather than depending on it.
// This is required to use it from the bootstrap module, whose jar is launched as is (so shouldn't require dependencies).
// This is done for the other use of it too, from the cache module, not to have to manage two ways of depending on it.
lazy val addDirectoriesSources = {
  unmanagedSourceDirectories.in(Compile) += {
    val baseDir = baseDirectory.in(LocalRootProject).value
    val directoriesDir = baseDir / "modules" / "directories" / "src" / "main" / "java"
    if (!directoriesDir.exists())
      gitLock.synchronized {
        if (!directoriesDir.exists()) {
          val cmd = Seq("git", "submodule", "update", "--init", "--recursive", "--", "modules/directories")
          runCommand(cmd, baseDir)
        }
      }

    directoriesDir
  }
}

lazy val addPathsSources = Seq(
  addDirectoriesSources,
  unmanagedSourceDirectories.in(Compile) ++= unmanagedSourceDirectories.in(Compile).in(paths).value
)
