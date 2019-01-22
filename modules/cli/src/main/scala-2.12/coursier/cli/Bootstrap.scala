package coursier
package cli

import java.io.File
import java.nio.file.Files

import caseapp._
import coursier.bootstrap.{Assembly, ClassLoaderContent, ClasspathEntry, LauncherBat}
import coursier.cli.options.BootstrapOptions

object Bootstrap extends CaseApp[BootstrapOptions] {

  private def createNativeBootstrap(
    options: BootstrapOptions,
    helper: Helper,
    mainClass: String
  ): Unit = {

    val files = helper.fetch(
      sources = options.artifactOptions.sources,
      javadoc = options.artifactOptions.javadoc,
      default = options.artifactOptions.default0,
      classifier0 = options.artifactOptions.classifier0,
      artifactTypes = options.artifactOptions.artifactTypes
    )

    val log: String => Unit =
      if (options.options.common.verbosityLevel >= 0)
        s => Console.err.println(s)
      else
        _ => ()

    val tmpDir = new File(options.options.target)

    try {
      coursier.extra.Native.create(
        mainClass,
        files,
        new File(options.options.output),
        tmpDir,
        log,
        verbosity = options.options.common.verbosityLevel
      )
    } finally {
      if (!options.options.keepTarget)
        coursier.extra.Native.deleteRecursive(tmpDir)
    }
  }

  def run(options: BootstrapOptions, args: RemainingArgs): Unit = {
    try bootstrap(options, args)
    catch {
      case e: BootstrapException =>
        Console.err.println(e.message)
        sys.exit(1)
    }
  }

  def bootstrap(options: BootstrapOptions, args: RemainingArgs): Unit = {

    val helper = new Helper(
      options.options.common,
      args.all,
      isolated = options.options.isolated,
      warnBaseLoaderNotFound = false
    )

    val output0 = new File(options.options.output)
    if (!options.options.force && output0.exists())
      throw new BootstrapException(
        s"Error: ${options.options.output} already exists, use -f option to force erasing it."
      )

    val mainClass =
      if (options.options.mainClass.isEmpty)
        helper.retainedMainClass
      else
        options.options.mainClass

    if (options.options.native)
      createNativeBootstrap(options, helper, mainClass)
    else {

      val (validProperties, wrongProperties) = options.options.property.partition(_.contains("="))
      if (wrongProperties.nonEmpty)
        throw new BootstrapException(s"Wrong -P / --property option(s):\n${wrongProperties.mkString("\n")}")

      val properties0 = validProperties.map { s =>
        s.split("=", 2) match {
          case Array(k, v) => k -> v
          case _ => sys.error("Cannot possibly happen")
        }
      }

      val javaOpts = options.options.javaOpt ++
        properties0.map { case (k, v) => s"-D$k=$v" }

      val (urls, files) =
        helper.fetchMap(
          sources = options.artifactOptions.sources,
          javadoc = options.artifactOptions.javadoc,
          default = options.artifactOptions.default0,
          classifier0 = options.artifactOptions.classifier0,
          artifactTypes = options.artifactOptions.artifactTypes
        ).toList.foldLeft((List.empty[String], List.empty[File])){
          case ((urls, files), (url, file)) =>
            if (options.options.assembly || options.options.standalone) (urls, file :: files)
            else if (options.options.embedFiles && url.startsWith("file:/")) (urls, file :: files)
            else (url :: urls, files)
        }

      val bat = new File(output0.getParentFile, s"${output0.getName}.bat")

      if (options.options.generateBat && !options.options.force && bat.exists())
        throw new BootstrapException(s"Error: $bat already exists, use -f option to force erasing it.")

      if (options.options.assembly)
        Assembly.create(
          files,
          javaOpts,
          mainClass,
          output0.toPath,
          rules = options.options.rules,
          withPreamble = options.options.preamble
        )
      else {

        val isolatedDeps = options.options.isolated.isolatedDeps(options.options.common.dependencyOptions.scalaVersion)

        val (done, isolatedArtifactFiles) =
          options.options.isolated.targets.foldLeft((Set.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
            case ((done, acc), target) =>

              // TODO Add non regression test checking that optional artifacts indeed land in the isolated loader URLs

              val m = helper.fetchMap(
                sources = options.artifactOptions.sources,
                javadoc = options.artifactOptions.javadoc,
                default = options.artifactOptions.default0,
                classifier0 = options.artifactOptions.classifier0,
                artifactTypes = options.artifactOptions.artifactTypes,
                subset = isolatedDeps.getOrElse(target, Seq.empty).toSet
              )

              val m0 = m.filterKeys(url => !done(url))
              val done0 = done ++ m0.keys

              val (subUrls, subFiles) =
                if (options.options.standalone)
                  (Nil, m0.values.toSeq)
                else
                  (m0.keys.toSeq, Nil)

              val updatedAcc = acc + (target -> (subUrls, subFiles))

              (done0, updatedAcc)
          }

        val parents = options.options.isolated.targets.toSeq.map { t =>
          val e = isolatedArtifactFiles.get(t)
          val urls = e.map(_._1).getOrElse(Nil).map { url =>
            ClasspathEntry.Url(url)
          }
          val files = e.map(_._2).getOrElse(Nil).map { f =>
            ClasspathEntry.Resource(
              f.getName,
              f.lastModified(),
              Files.readAllBytes(f.toPath)
            )
          }
          ClassLoaderContent(
            urls ++ files,
            t
          )
        }

        val main = {
          val doneFiles = isolatedArtifactFiles.toSeq.flatMap(_._2._2).toSet
          val urls0 = urls.filterNot(done).map { url =>
            ClasspathEntry.Url(url)
          }
          val files0 = files.filterNot(doneFiles).map { f =>
            ClasspathEntry.Resource(
              f.getName,
              f.lastModified(),
              Files.readAllBytes(f.toPath)
            )
          }
          ClassLoaderContent(urls0 ++ files0)
        }

        coursier.bootstrap.Bootstrap.create(
          parents :+ main,
          mainClass,
          output0.toPath,
          javaOpts,
          deterministic = options.options.deterministic,
          withPreamble = options.options.preamble,
          proguarded = options.options.proguarded
        )
      }

      if (options.options.generateBat)
        LauncherBat.create(
          bat.toPath,
          javaOpts
        )
    }
  }

  final class BootstrapException(val message: String, cause: Throwable = null) extends Exception(message, cause)

}
