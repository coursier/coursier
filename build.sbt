
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
    utest,
    Mima.previousArtifacts,
    coursierPrefix
  )

lazy val scalazJvm = scalaz.jvm
lazy val scalazJs = scalaz.js

lazy val bootstrap = project("bootstrap")
  .settings(
    pureJava,
    dontPublish,
    addPathsSources,
    // seems not to be automatically found with sbt 0.13.16-M1 :-/
    mainClass := Some("coursier.Bootstrap"),
    renameMainJar("bootstrap.jar")
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
  .dependsOn(coreJvm, cacheJvm, extra, scalazJvm)
  .enablePlugins(PackPlugin, SbtProguard)
  .settings(
    shared,
    dontPublishIn("2.10", "2.11"),
    coursierPrefix,
    unmanagedResources.in(Test) += packageBin.in(bootstrap).in(Compile).value,
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.caseApp,
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
    dontPublish,
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
    coreJvm,
    coreJs,
    testsJvm,
    testsJs,
    `proxy-tests`,
    paths,
    cacheJvm,
    cacheJs,
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
    val bootstrapJar = packageBin.in(bootstrap).in(Compile).value
    val source = packageBin.in(Compile).value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

    ZipUtil.addToZip(source, dest, Seq(
      "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath)
    ))

    dest
  }
}

lazy val addBootstrapInProguardedJar = {

  import java.nio.charset.StandardCharsets
  import java.nio.file.Files

  proguard.in(Proguard) := {
    val bootstrapJar = packageBin.in(bootstrap).in(Compile).value
    val source = proguardedJar.value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")
    val dest0 = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap-and-prelude.jar")

    // TODO Get from cli original JAR
    val manifest =
      s"""Manifest-Version: 1.0
         |Implementation-Title: ${name.value}
         |Implementation-Version: ${version.value}
         |Specification-Vendor: ${organization.value}
         |Specification-Title: ${name.value}
         |Implementation-Vendor-Id: ${organization.value}
         |Specification-Version: ${version.value}
         |Implementation-URL: ${homepage.value.getOrElse("")}
         |Implementation-Vendor: ${organization.value}
         |Main-Class: ${mainClass.in(Compile).value.getOrElse(sys.error("Main class not found"))}
         |""".stripMargin

    ZipUtil.addToZip(source, dest, Seq(
      "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
      "META-INF/MANIFEST.MF" -> manifest.getBytes(StandardCharsets.UTF_8)
    ))

    ZipUtil.addPrelude(dest, dest0)

    Seq(dest0)
  }
}

lazy val proguardedCli = Seq(
  proguardVersion.in(Proguard) := SharedVersions.proguard,
  proguardOptions.in(Proguard) ++= Seq(
    "-dontwarn",
    "-dontoptimize", // required since the switch to scala 2.12
    "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
    "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}",
    "-adaptresourcefilenames **.properties",
    """-keep class scala.Symbol { *; }"""
  ),
  javaOptions.in(Proguard, proguard) := Seq("-Xmx3172M"),
  artifactPath.in(Proguard) := proguardDirectory.in(Proguard).value / "coursier-standalone.jar",
  artifacts ++= {
    if (scalaBinaryVersion.value == "2.12")
      Seq(proguardedArtifact.value)
    else
      Nil
  },
  addBootstrapInProguardedJar,
  addProguardedJar
)

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
