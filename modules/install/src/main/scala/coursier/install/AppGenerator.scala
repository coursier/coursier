package coursier.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}
import java.time.Instant

import coursier.Fetch
import coursier.cache.Cache
import coursier.core.{Dependency, Repository}
import coursier.launcher.{AssemblyGenerator, BootstrapGenerator, ClassLoaderContent, ClassPathEntry, Generator, NativeImageGenerator, Parameters, Preamble, ScalaNativeGenerator}
import coursier.launcher.internal.{FileUtil, Windows}
import coursier.launcher.native.NativeBuilder
import coursier.launcher.Parameters.ScalaNative
import coursier.util.{Artifact, Task}

import scala.util.control.NonFatal

object AppGenerator {

  private def classpathEntry(a: Artifact, f: File, forceResource: Boolean = false): ClassPathEntry =
    if (forceResource || a.changing || a.url.startsWith("file:"))
      ClassPathEntry.Resource(
        f.getName,
        f.lastModified(),
        Files.readAllBytes(f.toPath)
      )
    else
      ClassPathEntry.Url(a.url)

  private def foundMainClassOpt(
    shared: Seq[File],
    jars: Seq[File],
    verbosity: Int,
    mainDependencyOpt: Option[Dependency]
  ): Option[String] = {
    val m = MainClass.mainClasses(jars)
    if (verbosity >= 2) {
      System.err.println(s"Found ${m.size} main classes:")
      for (a <- m)
        System.err.println(s"  $a")
    }
    MainClass.retainedMainClassOpt(m, mainDependencyOpt.map(d => (d.module.organization.value, d.module.name.value))) // appArtifacts.fetchResult.resolution.rootDependencies.headOption)
  }

  private def simpleFetch(cache: Cache[Task], repositories: Seq[Repository]): Seq[String] => Seq[File] = {
    val fetch  = coursier.Fetch(cache)
      .withRepositories(repositories)

    deps =>
      import coursier.core.ModuleName
      import coursier.{Dependency, Module, organizationString}
      import coursier.util.Task
      import coursier.core.Organization

      val deps0 = deps.map { dep =>
        dep.split(":", 3) match {
          case Array(org, name, ver) =>
            Dependency(Module(Organization(org), ModuleName(name)), ver)
          case _ => ???
        }
      }

      fetch.addDependencies(deps0: _*).run()
  }

  private def writing[T](
    path: Path,
    verbosity: Int,
    modifiedTime: Option[Instant] = None
  )(f: => T): T = {

    if (verbosity >= 2)
      System.err.println(s"Writing $path")
    val t = f
    for (time <- modifiedTime)
      Files.setLastModifiedTime(path, FileTime.from(time))
    if (verbosity >= 1)
      System.err.println(s"Wrote $path")
    t
  }

  def createOrUpdate(
    descOpt: Option[(AppDescriptor, Array[Byte])],
    sourceReprOpt: Option[Array[Byte]],
    cache: Cache[Task],
    baseDir: Path,
    dest: Path,
    currentTime: Instant = Instant.now(),
    verbosity: Int = 0,
    force: Boolean = false,
    graalvmParamsOpt: Option[GraalvmParams] = None,
    coursierRepositories: Seq[Repository] = Nil
  ): Boolean = {

    val dest0 =
      if (Windows.isWindows) dest.getParent.resolve(dest.getFileName.toString + ".bat")
      else dest
    val auxExtension =
      if (Windows.isWindows) ".exe"
      else ""

    // values before the `(tmpDest, tmpAux) =>` need to be evaluated when we hold the lock of Updatable.writing
    def update: (Path, Path) => Boolean = {

      val (desc, descRepr) = descOpt.getOrElse {
        if (Files.exists(dest0))
          InfoFile.readAppDescriptor(dest0) match {
            case None => throw new CannotReadAppDescriptionInLauncher(dest0)
            case Some(d) => d
          }
        else
          throw new LauncherNotFound(dest0)
      }

      val sourceReprOpt0 = sourceReprOpt.orElse {
        if (Files.exists(dest0))
          InfoFile.readSource(dest0).map(_._2)
        else
          None
      }

      val appArtifacts = AppArtifacts(desc, cache, verbosity)

      val lock0 = {
        val artifacts = appArtifacts.fetchResult.artifacts.filterNot(appArtifacts.shared.toSet)
        ArtifactsLock.ofArtifacts(artifacts)
      }

      val sharedLockOpt =
        if (appArtifacts.shared.isEmpty)
          None
        else
          Some(ArtifactsLock.ofArtifacts(appArtifacts.shared))

      lazy val upToDate = InfoFile.upToDate(
        dest0,
        lock0,
        sharedLockOpt,
        descRepr,
        sourceReprOpt0
      )

      val shouldUpdate = force || !upToDate

      lazy val mainClass = {

        def foundMainClassOpt0 =
          foundMainClassOpt(
            appArtifacts.shared.map(_._2),
            appArtifacts.fetchResult.artifacts.filterNot(appArtifacts.shared.toSet).map(_._2),
            verbosity,
            appArtifacts.fetchResult.resolution.rootDependencies.headOption
          )

        desc.mainClass
          .orElse(foundMainClassOpt0)
          .orElse(desc.defaultMainClass)
          .getOrElse {
            throw new NoMainClassFound
          }
      }

      lazy val infoEntries = InfoFile.extraEntries(lock0, sharedLockOpt, descRepr, sourceReprOpt0, currentTime)

      (tmpDest, tmpAux) =>
        if (shouldUpdate) {
          val params = desc.launcherType match {
            case LauncherType.DummyJar =>
              Parameters.Bootstrap(Nil, mainClass)
                .withPreamble(
                  Preamble()
                    .withJavaOpts(desc.javaOptions)
                )
                .withJavaProperties(desc.javaProperties ++ appArtifacts.extraProperties)
                .withDeterministic(true)
                .withHybridAssembly(desc.launcherType == LauncherType.Hybrid)
                .withExtraZipEntries(infoEntries)

            case _: LauncherType.BootstrapLike =>
              val isStandalone = desc.launcherType != LauncherType.Bootstrap
              val sharedContentOpt =
                if (appArtifacts.shared.isEmpty) None
                else {
                  val entries = appArtifacts.shared.map {
                    case (a, f) =>
                      classpathEntry(a, f, forceResource = isStandalone)
                  }

                  Some(ClassLoaderContent(entries))
                }
              val mainContent = ClassLoaderContent(
                appArtifacts.fetchResult.artifacts.map {
                  case (a, f) =>
                    classpathEntry(a, f, forceResource = isStandalone)
                }
              )

              Parameters.Bootstrap(sharedContentOpt.toSeq :+ mainContent, mainClass)
                .withPreamble(
                  Preamble()
                    .withJavaOpts(desc.javaOptions)
                )
                .withJavaProperties(desc.javaProperties ++ appArtifacts.extraProperties)
                .withDeterministic(true)
                .withHybridAssembly(desc.launcherType == LauncherType.Hybrid)
                .withExtraZipEntries(infoEntries)

            case LauncherType.Assembly =>

              assert(appArtifacts.shared.isEmpty) // just in case

              // FIXME Allow to adjust merge rules?
              Parameters.Assembly()
                .withPreamble(
                  Preamble()
                    .withJavaOpts(desc.javaOptions)
                )
                .withFiles(appArtifacts.fetchResult.files)
                .withMainClass(mainClass)
                .withExtraZipEntries(infoEntries)

            case LauncherType.DummyNative =>
              Parameters.DummyNative()

            case LauncherType.GraalvmNativeImage =>

              assert(appArtifacts.shared.isEmpty) // just in case

              val fetch = simpleFetch(cache, coursierRepositories)

              Parameters.NativeImage(mainClass)
                .withFetch(Some(fetch))
                .withGraalvmHome(graalvmParamsOpt.flatMap(_.home).map(new File(_)))
                .withGraalvmOptions(desc.graalvmOptions.toSeq.flatMap(_.options) ++ graalvmParamsOpt.map(_.extraNativeImageOptions).getOrElse(Nil))
                .withJars(appArtifacts.fetchResult.files)
                .withNameOpt(Some(desc.nameOpt.getOrElse(dest0.getFileName.toString)))
                .withVerbosity(verbosity)

            case LauncherType.ScalaNative =>

              assert(appArtifacts.shared.isEmpty) // just in case

              val fetch = simpleFetch(cache, coursierRepositories)
              val nativeVersion = appArtifacts.platformSuffixOpt
                .fold("" /* FIXME throw instead? */)(_.stripPrefix("_native"))
              // FIXME Allow options to be tweaked
              val options = ScalaNative.ScalaNativeOptions()

              Parameters.ScalaNative(fetch, mainClass, nativeVersion)
                .withJars(appArtifacts.fetchResult.files)
                .withOptions(options)
                .withVerbosity(verbosity)
          }

          if (desc.launcherType.isNative) {
            val preamble =
              if (Windows.isWindows)
                Preamble()
                  .withKind(Preamble.Kind.Bat)
                  .withCommand("%~dp0\\" + Updatable.auxName("%~n0", ".exe"))
              else
                Preamble()
                  .withKind(Preamble.Kind.Sh)
                  .withCommand(""""$(cd "$(dirname "$0")"; pwd)/""" + Updatable.auxName(dest0.getFileName.toString, "") + "\"") // FIXME needs directory
            writing(tmpDest, verbosity, Some(currentTime)) {
              InfoFile.writeInfoFile(tmpDest, Some(preamble), infoEntries)
              FileUtil.tryMakeExecutable(tmpDest)
            }
            writing(tmpAux, verbosity, Some(currentTime)) {
              Generator.generate(params, tmpAux)
            }
          } else
            writing(tmpDest, verbosity, Some(currentTime)) {
              Generator.generate(params, tmpDest)
            }
        }

        shouldUpdate
    }

    Updatable.writing(baseDir, dest0, auxExtension, verbosity) { (tmpDest, tmpAux) =>
      update(tmpDest, tmpAux)
    }.getOrElse {
      sys.error(s"Could not acquire lock for $dest0")
    }
  }

  sealed abstract class AppGeneratorException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

  final class NoMainClassFound extends AppGeneratorException("No main class found")

  // FIXME Keep more details
  final class NoScalaVersionFound extends AppGeneratorException("No scala version found")

  final class LauncherNotFound(path: Path) extends AppGeneratorException(s"$path not found")

  final class CannotReadAppDescriptionInLauncher(path: Path)
    extends AppGeneratorException(s"Cannot read app description in $path")

}
