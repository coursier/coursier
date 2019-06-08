
import Aliases._
import Settings.{crossProject, project, _}
import Publish._

lazy val getSbtCoursierVersion = settingKey[String]("")

getSbtCoursierVersion := {
  sbtCoursierVersion
}

inThisBuild(List(
  organization := "io.get-coursier",
  homepage := Some(url("https://github.com/coursier/coursier")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "alexandre.archambault@gmail.com",
      url("https://github.com/alexarchambault")
    )
  )
))

lazy val core = crossProject("core")(JSPlatform, JVMPlatform)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading("coursier.util.shaded"),
    utest,
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
      Deps.cross.fastParse.value,
      Deps.cross.scalaJsDom.value
    )
  )
  .settings(
    shared,
    coursierPrefix,
    dontPublishScalaJsIn("2.11"),
    Mima.previousArtifacts,
    Mima.coreFilters
  )

lazy val coreJvm = core.jvm
lazy val coreJs = core.js

lazy val tests = crossProject("tests")(JSPlatform, JVMPlatform)
  .dependsOn(core, coursier % Test)
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
    libs += Deps.scalaAsync.value,
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
      Deps.scalaAsync.value,
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
    utest,
    libs ++= {
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((2, 12)) =>
          Seq(
            Deps.http4sBlazeServer % Test,
            Deps.http4sDsl % Test,
            Deps.logbackClassic % Test,
            Deps.scalaAsync.value % Test
          )
        case _ =>
          Nil
      }
    },
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
    libs += Deps.cross.scalazCore.value
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
    libs += Deps.cross.catsEffect.value,
    onlyIn("2.11", "2.12"), // not there yet for 2.13.0-RC1
  )

lazy val catsJvm = cats.jvm
lazy val catsJs = cats.js

lazy val `bootstrap-launcher` = project("bootstrap-launcher")
  .enablePlugins(SbtProguard)
  .settings(
    pureJava,
    dontPublish,
    addPathsSources,
    mainClass.in(Compile) := Some("coursier.bootstrap.launcher.Launcher"),
    proguardedBootstrap("coursier.bootstrap.launcher.Launcher", resourceBased = false)
  )

lazy val `resources-bootstrap-launcher` = project("resources-bootstrap-launcher")
  .enablePlugins(SbtProguard)
  .settings(
    pureJava,
    dontPublish,
    unmanagedSourceDirectories.in(Compile) ++= unmanagedSourceDirectories.in(`bootstrap-launcher`, Compile).value,
    mainClass.in(Compile) := Some("coursier.bootstrap.launcher.ResourcesLauncher"),
    proguardedBootstrap("coursier.bootstrap.launcher.ResourcesLauncher", resourceBased = true)
  )

lazy val bootstrap = project("bootstrap")
  .settings(
    shared,
    coursierPrefix,
    addBootstrapJarAsResource
  )

lazy val benchmark = project("benchmark")
  .dependsOn(coursierJvm)
  .enablePlugins(JmhPlugin)
  .settings(
    shared,
    dontPublish,
    libraryDependencies += Deps.mavenModel
  )

lazy val publish = project("publish")
  .dependsOn(coreJvm, cacheJvm, okhttp)
  .settings(
    shared,
    coursierPrefix,
    libs ++= Seq(
      Deps.argonautShapeless,
      Deps.catsCore,
      Deps.emoji
    ),
    resolvers += Resolver.typesafeIvyRepo("releases"), // for "com.lightbend" %% "emoji"
    onlyIn("2.11", "2.12"), // not all dependencies there yet for 2.13
  )

lazy val cli = project("cli")
  .dependsOn(bootstrap, coursierJvm, okhttp, publish)
  .enablePlugins(ContrabandPlugin, PackPlugin)
  .settings(
    shared,
    // does this really work?
    skipGeneration in generateContrabands := {
      !isSbv("2.12").value
    },
    managedSourceDirectories.in(Compile) ++= {
      val baseDir = baseDirectory.value
      if (isSbv("2.12").value)
        Seq(baseDir / "src" / "main" / "contraband-scala")
      else
        Nil
    },
    sourceManaged.in(Compile, generateContrabands) := {
      val baseDir = baseDirectory.value
      val previous = sourceManaged.in(Compile, generateContrabands).value
      if (isSbv("2.12").value)
        baseDir / "src" / "main" / "contraband-scala"
      else
        previous
    },
    contrabandSource.in(Compile, generateContrabands) := {
      val current = contrabandSource.in(Compile, generateContrabands).value
      if (isSbv("2.12").value)
        current
      else
        current / "foo"
    },
    coursierPrefix,
    unmanagedResources.in(Test) ++= Seq(
      proguardedJar.in(`bootstrap-launcher`).in(Compile).value,
      proguardedJar.in(`resources-bootstrap-launcher`).in(Compile).value
    ),
    scalacOptions += "-Ypartial-unification",
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.argonautShapeless,
          Deps.caseApp,
          Deps.catsCore,
          Deps.junit % Test, // to be able to run tests with pants
          Deps.scalatest % Test
        )
      else
        Seq()
    },
    mainClass.in(Compile) := Some("coursier.cli.Coursier"),
    onlyIn("2.12")
  )

lazy val `cli-native_03` = project("cli-native_03")
  .dependsOn(cli)
  .settings(
    shared,
    name := "cli-native_0.3",
    moduleName := name.value,
    onlyPublishIn("2.12"),
    coursierPrefix,
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(Deps.scalaNativeTools03)
      else
        Seq()
    }
  )

lazy val `cli-native_040M2` = project("cli-native_040M2")
  .dependsOn(cli)
  .settings(
    shared,
    name := "cli-native_0.4.0-M2",
    moduleName := name.value,
    onlyPublishIn("2.12"),
    coursierPrefix,
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(Deps.scalaNativeTools040M2)
      else
        Seq()
    }
  )

lazy val web = project("web")
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(coursierJs)
  .settings(
    shared,
    onlyPublishIn("2.12"),
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.cross.scalaJsJquery.value,
          Deps.cross.scalaJsReact.value
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
      "requirejs" -> "2.3.6",
      "sax" -> "1.2.4"
    ),
    browserifyBundle("sax")
  )

lazy val okhttp = project("okhttp")
  .dependsOn(cacheJvm)
  .settings(
    shared,
    coursierPrefix,
    libs += Deps.okhttpUrlConnection
  )

lazy val coursier = crossProject("coursier")(JSPlatform, JVMPlatform)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading("coursier.internal.shaded"),
    // TODO shade those
    libs += Deps.fastParse % "shaded"
  )
  .jsSettings(
    libs += Deps.cross.fastParse.value
  )
  .dependsOn(core, cache)
  .configs(Integration)
  .settings(
    shared,
    hasITs,
    dontPublishScalaJsIn("2.11"),
    libs += Deps.scalaReflect.value % Provided,
    publishGeneratedSources,
    utest,
    libs ++= Seq(
      Deps.scalaAsync.value % Test,
      Deps.cross.argonautShapeless.value
    )
  )

lazy val coursierJvm = coursier.jvm
lazy val coursierJs = coursier.js

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
    `resources-bootstrap-launcher`,
    bootstrap,
    benchmark,
    publish,
    cli,
    okhttp,
    coursierJvm,
    `cli-native_03`,
    `cli-native_040M2`
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
    coursierJs
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-js"
  )

lazy val `coursier-repo` = project("coursier-repo")
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
    `resources-bootstrap-launcher`,
    bootstrap,
    benchmark,
    publish,
    cli,
    scalazJvm,
    scalazJs,
    web,
    okhttp,
    coursierJvm,
    coursierJs,
    `cli-native_03`,
    `cli-native_040M2`
  )
  .settings(
    shared,
    dontPublish
  )


lazy val addBootstrapJarAsResource = {

  import java.nio.file.Files

  packageBin.in(Compile) := {
    val originalBootstrapJar = packageBin.in(`bootstrap-launcher`).in(Compile).value
    val bootstrapJar = proguardedJar.in(`bootstrap-launcher`).in(Compile).value
    val originalResourcesBootstrapJar = packageBin.in(`resources-bootstrap-launcher`).in(Compile).value
    val resourcesBootstrapJar = proguardedJar.in(`resources-bootstrap-launcher`).in(Compile).value
    val source = packageBin.in(Compile).value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

    ZipUtil.addToZip(source, dest, Seq(
      "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
      "bootstrap-orig.jar" -> Files.readAllBytes(originalBootstrapJar.toPath),
      "bootstrap-resources.jar" -> Files.readAllBytes(resourcesBootstrapJar.toPath),
      "bootstrap-resources-orig.jar" -> Files.readAllBytes(originalResourcesBootstrapJar.toPath)
    ))

    dest
  }
}

def proguardedBootstrap(mainClass: String, resourceBased: Boolean): Seq[Setting[_]] = {

  val extra =
    if (resourceBased)
      Seq("-keep class coursier.bootstrap.launcher.jar.Handler {\n}")
    else
      Nil

  val fileName =
    if (resourceBased)
      "bootstrap-resources.jar"
    else
      "bootstrap.jar"

  Seq(
    proguardedJar := proguardedJarTask.value,
    proguardVersion.in(Proguard) := Deps.proguardVersion,
    proguardOptions.in(Proguard) ++= Seq(
      "-dontwarn",
      "-repackageclasses coursier.bootstrap.launcher",
      s"-keep class $mainClass {\n  public static void main(java.lang.String[]);\n}",
      "-keep class coursier.bootstrap.launcher.SharedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}"
    ) ++ extra,
    javaOptions.in(Proguard, proguard) := Seq("-Xmx3172M"),
    artifactPath.in(Proguard) := proguardDirectory.in(Proguard).value / fileName
  )
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
