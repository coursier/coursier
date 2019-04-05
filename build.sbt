
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
    shading,
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
      CrossDeps.fastParse.value,
      CrossDeps.scalaJsDom.value
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
            "org.http4s" %% "http4s-blaze-server" % "0.18.17" % Test,
            "org.http4s" %% "http4s-dsl" % "0.18.17" % Test,
            "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
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
    libs += CrossDeps.catsEffect.value,
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
    libraryDependencies += "org.apache.maven" % "maven-model" % "3.6.1"
  )

lazy val cli = project("cli")
  .dependsOn(bootstrap, coursierJvm)
  .enablePlugins(ContrabandPlugin, PackPlugin, SbtProguard)
  .settings(
    shared,
    onlyPublishIn("2.12"),
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
          Deps.scalaNativeTools,
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
    proguardedCli
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
    shading,
    // TODO shade those
    libs += Deps.fastParse % "shaded"
  )
  .jsSettings(
    libs += CrossDeps.fastParse.value
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
      CrossDeps.argonautShapeless.value
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
    cli,
    okhttp,
    coursierJvm
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
    cli,
    scalazJvm,
    scalazJs,
    web,
    okhttp,
    coursierJvm,
    coursierJs
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

lazy val proguardedJarWithBootstrap = Def.task {

  import java.nio.file.Files

  val bootstrapJar = proguardedJar.in(`bootstrap-launcher`).in(Compile).value
  val origBootstrapJar = packageBin.in(`bootstrap-launcher`).in(Compile).value
  val resourcesBootstrapJar = proguardedJar.in(`resources-bootstrap-launcher`).in(Compile).value
  val origResourcesBootstrapJar = packageBin.in(`resources-bootstrap-launcher`).in(Compile).value
  val source = proguardedJar.value

  val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")
  val dest0 = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap-and-prelude.jar")

  ZipUtil.addToZip(source, dest, Seq(
    "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
    "bootstrap-orig.jar" -> Files.readAllBytes(origBootstrapJar.toPath),
    "bootstrap-resources.jar" -> Files.readAllBytes(resourcesBootstrapJar.toPath),
    "bootstrap-resources-orig.jar" -> Files.readAllBytes(origResourcesBootstrapJar.toPath)
  ))

  ZipUtil.addPrelude(dest, dest0)

  dest0
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
    proguardVersion.in(Proguard) := SharedVersions.proguard,
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
    "-keep class coursier.cli.SharedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}",
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
