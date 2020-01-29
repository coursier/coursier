
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale

import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{scriptedBufferLog, scriptedLaunchOpts}
import com.lightbend.sbt.SbtProguard
import com.lightbend.sbt.SbtProguard.autoImport._
import com.jsuereth.sbtpgp._
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts
import coursier.ShadingPlugin.autoImport._
import Aliases._
import ScalaVersion._
import sbt.util.FileInfo
import scalajsbundler.Npm

object Settings {

  lazy val scalazBintrayRepository = {
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
  }

  def sonatypeRepository(name: String) = {
    resolvers += Resolver.sonatypeRepo(name)
  }

  lazy val localM2Repository = {
    resolvers += Resolver.mavenLocal
  }

  private lazy val isAtLeastScala213 = Def.setting {
    import Ordering.Implicits._
    CrossVersion.partialVersion(scalaVersion.value).exists(_ >= (2, 13))
  }

  lazy val javaScalaPluginShared = Seq(
    scalazBintrayRepository,
    sonatypeRepository("releases"),
    crossScalaVersions := ScalaVersion.versions, // defined for all projects to trump sbt-doge
    scalacOptions ++= Seq(
      "-target:jvm-1.8",
      "-feature",
      "-deprecation",
      "-language:higherKinds",
      "-language:implicitConversions"
    ),
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaBinaryVersion.value) match {
        case Some((2, min)) if min <= 12 => Seq("-Ypartial-unification")
        case _ => Nil
      }
    },
    javacOptions ++= Seq(
      "-source", "1.8",
      "-target", "1.8"
    ),
    javacOptions.in(Keys.doc) := Seq(),
    libraryDependencies ++= {
      if (isAtLeastScala213.value) Nil
      else Seq(compilerPlugin("org.scalamacros" % s"paradise" % "2.1.1" cross CrossVersion.full))
    },
    scalacOptions ++= {
      if (isAtLeastScala213.value) Seq("-Ymacro-annotations")
      else Nil
    }
  ) ++ {
    val prop = sys.props.getOrElse("publish.javadoc", "").toLowerCase(Locale.ROOT)
    if (prop == "0" || prop == "false")
      Seq(
        sources in (Compile, doc) := Seq.empty,
        publishArtifact in (Compile, packageDoc) := false
      )
    else
      Nil
  }

  def doRunNpmInstallIfNeeded(baseDir: File, log: Logger): Unit = {
    val evFile = baseDir / "node_modules" / ".npm_run"
    if (!evFile.exists()) {
      val cmd = Seq("npm", "install")
      val b = new ProcessBuilder(cmd: _*)
      b.directory(baseDir)
      b.inheritIO()
      log.info(s"Running  ${cmd.mkString(" ")}")
      val p = b.start()
      val retCode = p.waitFor()
      if (retCode == 0)
        log.info(s"${cmd.mkString(" ")}  ran successfully")
      else
        sys.error(s"${cmd.mkString(" ")}  failed (return code $retCode)")

      // Parent dir should have been created by npm install
      Files.write(evFile.toPath, Array.emptyByteArray)
    }
  }

  val runNpmInstallIfNeeded = Def.task {
    val baseDir = baseDirectory.in(ThisBuild).value
    val log = streams.value.log
    doRunNpmInstallIfNeeded(baseDir, log)
  }

  lazy val shared = javaScalaPluginShared ++ Seq(
    scalaVersion := scala212
  )

  lazy val pureJava = javaScalaPluginShared ++ Seq(
    crossPaths := false,
    autoScalaLibrary := false
  )

  lazy val generatePropertyFile =
    resourceGenerators.in(Compile) += Def.task {
      import sys.process._

      val dir = classDirectory.in(Compile).value / "coursier"
      val ver = version.value

      val f = dir / "coursier.properties"
      dir.mkdirs()

      val p = new java.util.Properties

      p.setProperty("version", ver)
      p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)

      val w = new java.io.FileOutputStream(f)
      p.store(w, "Coursier properties")
      w.close()

      state.value.log.info(s"Wrote $f")

      Nil
    }

  lazy val coursierPrefix = {
    name := "coursier-" + name.value
  }

  lazy val noTests = Seq(
    test.in(Test) := {},
    testOnly.in(Test) := {}
  )

  lazy val utest = Seq(
    libs += Deps.cross.utest.value % Test,
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  lazy val webjarBintrayRepository = {
    resolvers += "Webjars Bintray" at "https://dl.bintray.com/webjars/maven/"
  }

  lazy val divertThingsPlugin = {

    val actualSbtBinaryVersion = Def.setting(
      sbtBinaryVersion.in(pluginCrossBuild).value.split('.').take(2).mkString(".")
    )

    val sbtPluginScalaVersions = Map(
      "1.0"  -> "2.12"
    )

    val sbtScalaVersionMatch = Def.setting {
      val sbtVer = actualSbtBinaryVersion.value
      val scalaVer = scalaBinaryVersion.value

      sbtPluginScalaVersions.get(sbtVer).toSeq.contains(scalaVer)
    }

    Seq(
      baseDirectory := {
        val baseDir = baseDirectory.value

        if (sbtScalaVersionMatch.value)
          baseDir
        else
          baseDir / "target" / "dummy"
      },
      // Doesn't work, the second publish or publishLocal seem not to reference the previous implementation of the key.
      // This only seems to prevent ivy.xml files to be published locally anyway…
      // See also similar case in Publish.scala.
      // publish := Def.taskDyn {
      //   if (sbtScalaVersionMatch.value)
      //     publish
      //   else
      //     Def.task(())
      // },
      // publishLocal := Def.taskDyn {
      //   if (sbtScalaVersionMatch.value)
      //     publishLocal
      //   else
      //     Def.task(())
      // },
      publishArtifact := {
        sbtScalaVersionMatch.value && publishArtifact.value
      }
    )
  }

  val sbt10Version = "1.0.2"

  val pluginOverrideCrossScalaVersion = Seq(
    crossScalaVersions := Seq(scala212)
  )

  lazy val plugin =
    javaScalaPluginShared ++
    divertThingsPlugin ++
    withScriptedTests ++
    Seq(
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.version=" + version.value,
        "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath
      ),
      scriptedBufferLog := false,
      sbtPlugin := {
        scalaBinaryVersion.value match {
          case "2.12" => true
          case _ => false
        }
      },
      sbtVersion.in(pluginCrossBuild) := {
        scalaBinaryVersion.value match {
          case "2.12" => sbt10Version
          case _ => sbtVersion.in(pluginCrossBuild).value
        }
      }
    )

  def shading(namespace: String) =
    inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings) ++
       // Why does this have to be repeated here?
       // Can't figure out why configuration gets lost without this in particular...
      _root_.coursier.ShadingPlugin.projectSettings ++
      Seq(
        shadingNamespace := namespace,
        publish := publish.in(Shading).value,
        publishLocal := publishLocal.in(Shading).value,
        PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value,
        PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
      )

  // adapted from https://github.com/sbt/sbt-proguard/blob/2c502f961245a18677ef2af4220a39e7edf2f996/src/main/scala/com/typesafe/sbt/SbtProguard.scala#L83-L100
  lazy val proguardTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    SbtProguard.writeConfiguration(proguardConfiguration.in(Proguard).value, proguardOptions.in(Proguard).value)
    val proguardConfigurationValue = proguardConfiguration.in(Proguard).value
    val javaOptionsInProguardValue = (javaOptions in proguard).value
    val managedClasspathValue = managedClasspath.in(Proguard).value
    val streamsValue = streams.value
    val outputsValue = proguardOutputs.in(Proguard).value
    val cachedProguard = FileFunction.cached(streams.value.cacheDirectory / "proguard", FilesInfo.hash) { _ =>
      outputsValue foreach IO.delete
      streamsValue.log.debug("Proguard configuration:")
      proguardOptions.in(Proguard).value foreach (streamsValue.log.debug(_))
      runProguard(proguardConfigurationValue, javaOptionsInProguardValue, managedClasspathValue.files, streamsValue.log)
      outputsValue.toSet
    }
    val inputs = (proguardConfiguration.in(Proguard).value +: SbtProguard.inputFiles(proguardFilteredInputs.in(Proguard).value)).toSet

    // coursier-specific: more aggressive existing file re-use (ran into suspicious multiple runs of proguard on Travis CI)
    if (outputsValue.exists(!_.exists()))
      cachedProguard(inputs)

    outputsValue
  }

  def runProguard(config: File, javaOptions: Seq[String], classpath: Seq[File], log: Logger): Unit = {
    require(classpath.nonEmpty, "Proguard classpath cannot be empty!")
    val options = javaOptions ++ Seq("-cp", Path.makeString(classpath), "proguard.ProGuard", "-include", config.getAbsolutePath)
    log.debug("Proguard command:")
    log.debug("java " + options.mkString(" "))

    val t = new Thread("proguard-sentinel") {
      setDaemon(true)
      override def run() =
        try {
          while (true) {
            Thread.sleep(10000L)
            scala.Console.err.println("Proguard still running")
          }
        } catch {
          case _: InterruptedException =>
            scala.Console.err.println("Proguard not running anymore")
            // normal exit
        }
    }

    try {
      t.start()
      val exitCode = sys.process.Process("java", options) ! log
      if (exitCode != 0) sys.error("Proguard failed with exit code [%s]" format exitCode)
    } finally {
      if (t.isAlive)
        t.interrupt()
    }
  }

  lazy val proguardedJar = taskKey[File]("")

  lazy val proguardedJarTask = Def.task {

    val results = proguardTask.value

    val orig = results match {
      case Seq(f0) => f0
      case Seq() =>
        throw new Exception("Found no proguarded files. Expected one.")
      case _ =>
        throw new Exception("Found several proguarded files. Don't know how to publish all of them.")
    }

    val destDir = new File(orig.getParentFile, "with-meta-inf")
    destDir.mkdirs()

    val dest = new File(destDir, orig.getName)
    Files.deleteIfExists(dest.toPath)

    // TODO Get from original JAR
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

    ZipUtil.addToZip(orig, dest, Seq(
      "META-INF/MANIFEST.MF" -> manifest.getBytes(StandardCharsets.UTF_8)
    ))

    dest
  }

  lazy val Integration = config("it").extend(Test)

  def runCommand(cmd: Seq[String], dir: File): Unit = {
    val b = new ProcessBuilder(cmd: _*)
    b.directory(dir)
    b.inheritIO()
    val p = b.start()
    val retCode = p.waitFor()
    if (retCode != 0)
      sys.error(s"Command ${cmd.mkString(" ")} failed (return code $retCode)")
  }

  val gitLock = new Object

  // macros could get the ids automatically…

  def crossProject(dir: String, id: String)(platforms: sbtcrossproject.Platform*): sbtcrossproject.CrossProject.Builder =
    sbtcrossproject.CrossProject(id, file(s"modules/$dir/$id"))(platforms: _*)

  def crossProject(id: String)(platforms: sbtcrossproject.Platform*): sbtcrossproject.CrossProject.Builder =
    sbtcrossproject.CrossProject(id, file(s"modules/$id"))(platforms: _*)

  def project(id: String) =
    Project(id, file(s"modules/$id"))

  def browserifyBundle(packages: String*) =
    Seq(
      managedResources.in(Compile) += {

        val s = streams.value
        val baseDir = baseDirectory.in(ThisBuild).value

        val packagesFile = target.value / "browserify-packages.txt"
        Files.write(packagesFile.toPath, packages.mkString("\n").getBytes(StandardCharsets.UTF_8))

        val output = target.value / "browserify" / packages.mkString("-") / "bundle.js"

        val f = FileFunction.cached(
          s.cacheDirectory / "browserify-bundle",
          FileInfo.hash
        ) { _ =>

          doRunNpmInstallIfNeeded(baseDir, s.log)

          output.getParentFile.mkdirs()
          val args = Seq("run", "browserify", "--", "-o", output.getAbsolutePath) ++
            packages.flatMap(p => Seq("-r", p))
          Npm.run(args: _*)(baseDir, s.log)
          Set.empty
        }

        f(Set(packagesFile))

        output
      }
    )

  lazy val publishGeneratedSources = Seq(
    // https://github.com/sbt/sbt/issues/2205
    mappings in (Compile, packageSrc) ++= {
      val srcs = (managedSources in Compile).value
      val sdirs = (managedSourceDirectories in Compile).value
      val base = baseDirectory.value
      (srcs --- sdirs --- base).pair(Path.relativeTo(sdirs) | Path.relativeTo(base) | Path.flat)
    }
  )

  def onlyIn(sbv: String*) = {

    val sbv0 = sbv.toSet
    val ok = Def.setting {
      CrossVersion.partialVersion(scalaBinaryVersion.value)
        .map { case (maj, min) => s"$maj.$min" }
        .exists(sbv0)
    }

    Seq(
      baseDirectory := {
        val baseDir = baseDirectory.value

        if (ok.value)
          baseDir
        else
          baseDir / "target" / "dummy"
      },
      libraryDependencies := {
        val deps = libraryDependencies.value
        if (ok.value)
          deps
        else
          Nil
      },
      publishArtifact := ok.value,
      mainClass.in(Compile) := {
        val previous = mainClass.in(Compile).value
        if (ok.value)
          previous
        else
          None
      },
      mimaPreviousArtifacts := {
        val previous = mimaPreviousArtifacts.?.value
        if (ok.value)
          previous.getOrElse(Set.empty)
        else
          Set.empty
      }
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
        "-dontnote",
        "-dontwarn",
        "-repackageclasses coursier.bootstrap.launcher",
        s"-keep class $mainClass {\n  public static void main(java.lang.String[]);\n}",
        "-keep class coursier.bootstrap.launcher.SharedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}"
      ) ++ extra,
      javaOptions.in(Proguard, proguard) := Seq("-Xmx3172M"),
      artifactPath.in(Proguard) := proguardDirectory.in(Proguard).value / fileName
    )
  }

  lazy val javaMajorVer = sys.props("java.version").takeWhile(_.isDigit).toInt

}
