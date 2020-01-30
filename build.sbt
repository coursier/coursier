
import Aliases._
import Settings.{crossProject, project, _}
import Publish._

lazy val warn = {
  try {
    Thread.currentThread()
      .getContextClassLoader
      .loadClass("coursier.Resolve")
  } catch {
    case _: ClassNotFoundException =>
      val (red, reset) =
        if (sys.env.contains("CI") || java.lang.Boolean.getBoolean("sbt.log.noformat"))
          ("", "")
	else
	  (scala.Console.YELLOW, scala.Console.RESET)

      System.err.println(
        s"""$red
           |Warning: It seems you're using the default sbt launcher.
           |The coursier build currently requires using the coursier-based
           |sbt-launcher (https://github.com/coursier/sbt-launcher).
           |Use
           |  ./sbt
           |to run the coursier-based launcher instead.
           |$reset""".stripMargin
      )
 }
}

version := {
  warn
  version.value
}

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

lazy val util = crossProject("util")(JSPlatform, JVMPlatform)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading("coursier.util.shaded"),
    libs ++= Seq(
      Deps.jsoup % "shaded"
    ),
    shadeNamespaces ++= Set("org.jsoup")
  )
  .settings(
    shared,
    coursierPrefix,
    Mima.previousArtifacts,
    Mima.utilFilters,
    dontPublishScalaJsIn("2.11"),
    libs += Deps.dataClass % Provided
  )

lazy val utilJvm = util.jvm
lazy val utilJs = util.js

lazy val core = crossProject("core")(JSPlatform, JVMPlatform)
  .dependsOn(util)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading("coursier.core.shaded"),
    utest,
    libs ++= Seq(
      Deps.fastParse % "shaded",
      Deps.scalaXml
    ),
    shadeNamespaces ++= Set(
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
    Mima.previousArtifacts,
    Mima.coreFilters,
    dontPublishScalaJsIn("2.11"),
    libs += Deps.dataClass % Provided
  )

lazy val coreJvm = core.jvm
lazy val coreJs = core.js

lazy val tests = crossProject("tests")(JSPlatform, JVMPlatform)
  .disablePlugins(MimaPlugin)
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
    libs += Deps.scalaAsync,
    utest,
    sharedTestResources
  )

lazy val testsJvm = tests.jvm
lazy val testsJs = tests.js

lazy val `proxy-tests` = project("proxy-tests")
  .disablePlugins(MimaPlugin)
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
  .disablePlugins(MimaPlugin)
  .settings(
    pureJava,
    dontPublish,
    addDirectoriesSources
  )

lazy val cache = crossProject("cache")(JSPlatform, JVMPlatform)
  .dependsOn(util)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading("coursier.cache.shaded"),
    shadeNamespaces ++= Set("io.github.soc"),
    addPathsSources,
    libraryDependencies ++= Seq(
      Deps.svm % Provided,
      Deps.windowsAnsi
    )
  )
  .jsSettings(
    name := "fetch-js",
    libs += Deps.cross.scalaJsDom.value
  )
  .settings(
    shared,
    coursierPrefix,
    utest,
    libs += Deps.dataClass % Provided,
    libs ++= {
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((2, 12)) =>
          Seq(
            Deps.http4sBlazeServer % Test,
            Deps.http4sDsl % Test,
            Deps.logbackClassic % Test,
            Deps.scalaAsync % Test
          )
        case _ =>
          Nil
      }
    },
    Mima.previousArtifacts,
    Mima.cacheFilters,
    dontPublishScalaJsIn("2.11")
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
    coursierPrefix,
    utest,
    Mima.previousArtifacts,
    dontPublishScalaJsIn("2.11")
  )

lazy val scalazJvm = scalaz.jvm
lazy val scalazJs = scalaz.js

lazy val cats = crossProject("interop", "cats")(JSPlatform, JVMPlatform)
  .dependsOn(cache, tests % "test->test")
  .settings(
    name := "cats-interop",
    shared,
    utest,
    coursierPrefix,
    libs += Deps.cross.catsEffect.value,
    Mima.previousArtifacts,
    Mima.catsInteropFilters,
    dontPublishScalaJsIn("2.11")
  )

lazy val catsJvm = cats.jvm
lazy val catsJs = cats.js

lazy val `bootstrap-launcher` = project("bootstrap-launcher")
  .enablePlugins(SbtProguard)
  .disablePlugins(MimaPlugin)
  .settings(
    pureJava,
    dontPublish,
    addPathsSources,
    mainClass.in(Compile) := Some("coursier.bootstrap.launcher.Launcher"),
    proguardedBootstrap("coursier.bootstrap.launcher.Launcher", resourceBased = false)
  )

lazy val `resources-bootstrap-launcher` = project("resources-bootstrap-launcher")
  .enablePlugins(SbtProguard)
  .disablePlugins(MimaPlugin)
  .settings(
    pureJava,
    dontPublish,
    unmanagedSourceDirectories.in(Compile) ++= unmanagedSourceDirectories.in(`bootstrap-launcher`, Compile).value,
    mainClass.in(Compile) := Some("coursier.bootstrap.launcher.ResourcesLauncher"),
    proguardedBootstrap("coursier.bootstrap.launcher.ResourcesLauncher", resourceBased = true)
  )

lazy val bootstrap = project("bootstrap")
  .disablePlugins(MimaPlugin)
  .settings(
    shared,
    coursierPrefix,
    addBootstrapJarAsResource
  )

lazy val benchmark = project("benchmark")
  .dependsOn(coursierJvm)
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    shared,
    dontPublish,
    libraryDependencies += Deps.mavenModel
  )

lazy val publish = project("publish")
  .disablePlugins(MimaPlugin)
  .dependsOn(coreJvm, cacheJvm)
  .settings(
    shared,
    coursierPrefix,
    libs ++= Seq(
      Deps.argonautShapeless,
      Deps.catsCore,
      Deps.emoji,
      Deps.okhttp
    ),
    resolvers += Resolver.typesafeIvyRepo("releases"), // for "com.lightbend" %% "emoji"
    onlyIn("2.11", "2.12"), // not all dependencies there yet for 2.13
  )

lazy val install = project("install")
  .disablePlugins(MimaPlugin)
  .dependsOn(bootstrap, coursierJvm)
  .settings(
    shared,
    coursierPrefix,
    libs ++= Seq(
      Deps.argonautShapeless,
      Deps.catsCore,
      Deps.scalatest % Test
    ),
    onlyIn("2.12"),
    addBootstrapJarResourceInTests
  )

lazy val cli = project("cli")
  .dependsOn(bootstrap, coursierJvm, install, publish)
  .enablePlugins(PackPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    shared,
    coursierPrefix,
    addBootstrapJarResourceInTests,
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.argonautShapeless,
          Deps.caseApp,
          Deps.catsCore,
          Deps.monadlessCats,
          Deps.monadlessStdlib,
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
  .disablePlugins(MimaPlugin)
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
  .disablePlugins(MimaPlugin)
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
  .disablePlugins(MimaPlugin)
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
  .disablePlugins(MimaPlugin)
  .dependsOn(cacheJvm)
  .settings(
    shared,
    coursierPrefix,
    libs += Deps.okhttpUrlConnection
  )

lazy val coursier = crossProject("coursier")(JSPlatform, JVMPlatform)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading("coursier.core.shaded"), // shading only fastparse, that core shades too, so shading things under the same namespace
    libs += Deps.fastParse % "shaded",
    shadeNamespaces ++= Set(
      "fastparse",
      "sourcecode"
    ),
    Mima.previousArtifacts,
    Mima.coursierFilters
  )
  .jsConfigure(_.disablePlugins(MimaPlugin))
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
      Deps.scalaAsync % Test,
      Deps.cross.argonautShapeless.value,
      Deps.dataClass % Provided
    )
  )

lazy val coursierJvm = coursier.jvm
lazy val coursierJs = coursier.js

lazy val docs = project("docs")
  .in(file("doc"))
  .enablePlugins(MdocPlugin)
  .dependsOn(coursierJvm, catsJvm)
  .settings(
    shared,
    mdocIn := file("doc/docs"),
    mdocOut := file("doc/processed-docs"),
    mdocVariables := {
      def extraSbt(v: String) =
        if (v.endsWith("SNAPSHOT"))
          """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
        else
          ""
      val version0 = version.value
      val sv = scalaVersion.value
      Map(
        "VERSION" -> version0,
        "EXTRA_SBT" -> extraSbt(version0),
        "PLUGIN_VERSION" -> sbtCoursierVersion,
        "PLUGIN_EXTRA_SBT" -> extraSbt(sbtCoursierVersion),
        "SCALA_VERSION" -> sv
      )
    }
  )

lazy val jvm = project("jvm")
  .dummy
  .aggregate(
    utilJvm,
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
    install,
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
    utilJs,
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
  .disablePlugins(MimaPlugin)
  .in(root)
  .aggregate(
    utilJvm,
    utilJs,
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
    install,
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

  packageBin.in(Compile) := Def.taskDyn {

    val resourcesBootstrapLauncherOptTask: Def.Initialize[Task[Option[File]]] =
      if (javaMajorVer > 8)
        Def.task(None)
      else
        Def.task {
          Some(proguardedJar.in(`resources-bootstrap-launcher`).in(Compile).value)
        }

    val originalBootstrapJar = packageBin.in(`bootstrap-launcher`).in(Compile).value
    val bootstrapJar = proguardedJar.in(`bootstrap-launcher`).in(Compile).value
    val originalResourcesBootstrapJar = packageBin.in(`resources-bootstrap-launcher`).in(Compile).value
    val source = packageBin.in(Compile).value

    Def.task {
      val resourcesBootstrapJarOpt: Option[File] = resourcesBootstrapLauncherOptTask.value

      val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

      ZipUtil.addToZip(source, dest, Seq(
        "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
        "bootstrap-orig.jar" -> Files.readAllBytes(originalBootstrapJar.toPath),
        "bootstrap-resources-orig.jar" -> Files.readAllBytes(originalResourcesBootstrapJar.toPath)
      ) ++ resourcesBootstrapJarOpt.map { resourcesBootstrapJar =>
        "bootstrap-resources.jar" -> Files.readAllBytes(resourcesBootstrapJar.toPath)
      })

      dest
    }
  }.value
}

lazy val addBootstrapJarResourceInTests = Seq(
  unmanagedResources.in(Test) += proguardedJar.in(`bootstrap-launcher`).in(Compile).value,
  unmanagedResources.in(Test) ++= Def.taskDyn[Seq[File]] {
    if (javaMajorVer > 8)
    // Running into obscure proguard issues when building that one with JDK 11…
      Def.task(Nil)
    else
      Def.task {
        Seq(proguardedJar.in(`resources-bootstrap-launcher`).in(Compile).value)
      }
  }.value
)

lazy val addPathsSources = Seq(
  addDirectoriesSources,
  unmanagedSourceDirectories.in(Compile) ++= unmanagedSourceDirectories.in(Compile).in(paths).value
)
