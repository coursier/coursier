package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.Locale

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.cache.Cache
import coursier.cli.app.{AppDescriptor, RawAppDescriptor, RawSource, Source}
import coursier.cli.util.Guard
import coursier.core.{Module, Repository}
import coursier.util.{Sync, Task}

object Install extends CaseApp[InstallOptions] {

  def appDescriptor(
    channels: Seq[Module],
    repositories: Seq[Repository],
    cache: Cache[Task],
    id: String,
    verbosity: Int = 0
  ): Either[String, (Source, Array[Byte], AppDescriptor)] =
    for {

      t0 <- Channels.find(channels, id, cache, repositories).toRight {
        s"Cannot find app $id in channels ${channels.mkString(", ")}"
      }
      (channel, _, descRepr) = t0

      _ = if (verbosity >= 1)
        System.err.println(s"Found app $id in channel $channel")

      strRepr = new String(descRepr, StandardCharsets.UTF_8)

      t1 <- RawAppDescriptor.parse(strRepr) match {
        case Left(error) =>
          if (verbosity >= 2)
            System.err.println(s"Malformed app descriptor:\n$strRepr")
          Left(s"Error parsing app descriptor for app $id from channel $channel: $error")
        case Right(desc) =>
          val source = Source(repositories, channel, id)
          // FIXME Get raw repositories from or along with params.repositories
          Right((source, desc))
      }
      (source, rawDesc) = t1

      desc <- rawDesc.appDescriptor.toEither
        .left.map(errors => errors.toList.mkString(", "))

    } yield (source, descRepr, desc)

  def run(options: InstallOptions, args: RemainingArgs): Unit = {

    Guard()

    val params = InstallParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
    }

    if (params.shared.verbosity >= 1)
      System.err.println(s"Using bin directory ${params.shared.dir}")

    if (Files.exists(params.shared.dir)) {
      if (params.shared.verbosity >= 0 && !Files.isDirectory(params.shared.dir))
        System.err.println(s"Warning: ${params.shared.dir} doesn't seem to be a directory")
    } else
      Files.createDirectories(params.shared.dir)

    val pool = Sync.fixedThreadPool(params.shared.cache.parallel)
    val cache = params.shared.cache.cache(pool, params.shared.logger())

    val (sourceOpt, appDescriptorOpt) = args.all match {
      case Seq(id) if id.count(_ == ':') <= 1 =>
        if (params.channels.isEmpty) {
          System.err.println(s"Error: app id specified, but no channels passed")
          sys.exit(1)
        } else if (params.rawAppDescriptor.copy(repositories = Nil).isEmpty) {

          val (actualId, overrideVersionOpt) = {
            val idx = id.indexOf(':')
            if (idx < 0)
              (id, None)
            else
              (id.take(idx), Some(id.drop(idx + 1)))
          }

          appDescriptor(params.channels, params.repositories, cache, actualId, params.shared.verbosity) match {
            case Left(err) =>
              System.err.println(err)
              sys.exit(1)
            case Right((source, repr, desc)) =>
              val rawSource = RawSource(options.appOptions.repository, source.channel.toString, id)
              (Some((rawSource, source.copy(id = id))), Some((repr, overrideVersionOpt.fold(desc)(desc.overrideVersion))))
          }
        } else {
          import caseapp.core.util.NameOps._
          val argNames = InstallAppOptions.help.args.filter(_.name.name != "repository").map(_.name.option)
          System.err.println(
            s"App description arguments (${argNames.mkString(", ")}) can only be specified along with standard dependencies, not with app ids"
          )
          sys.exit(1)
        }
      case deps =>
        val descOpt = Some(params.rawAppDescriptor.copy(dependencies = deps.toList))
          .filter(!_.isEmpty)
          .map { desc =>
            desc.appDescriptor.toEither match {
              case Left(errors) =>
                for (err <- errors.toList)
                  System.err.println(err)
                sys.exit(1)
              case Right(d) => (desc.repr.getBytes(StandardCharsets.UTF_8), d)
            }
          }

        (None, descOpt)
    }

    val name = params.nameOpt
      .orElse(appDescriptorOpt.flatMap(_._2.nameOpt))
      .orElse(sourceOpt.map(_._2.id.takeWhile(_ != ':')))
      .getOrElse {
        System.err.println(s"No name specified. Pass one with the --name option.")
        sys.exit(1)
      }
    val dest = params.shared.dir.resolve(name)

    if (args.all.isEmpty && !Files.exists(dest)) {
      System.err.println(s"$dest not found and no dependency specified")
      sys.exit(1)
    }

    val wroteSomething = AppGenerator.createOrUpdate(
      appDescriptorOpt.map(t => (t._2, t._1)),
      sourceOpt.map(_._1.repr.getBytes(StandardCharsets.UTF_8)),
      cache,
      params.shared.dir,
      dest,
      Instant.now(),
      params.shared.verbosity,
      params.shared.forceUpdate,
      params.shared.graalvmParamsOpt,
      coursierRepositories = params.repositories
    )

    if (wroteSomething)
      System.err.println(s"Wrote $dest")
    else if (params.shared.verbosity >= 1)
      System.err.println(s"$dest doesn't need updating")

    if (params.shared.verbosity >= 0) {
      val path = sys.env.get("PATH")
        .orElse(sys.env.find(_._1.toLowerCase(Locale.ROOT) == "path").map(_._2)) // Windows
        .toSeq
        .flatMap(_.split(File.pathSeparatorChar).toSeq)
        .toSet

      if (!path(params.shared.dir.toAbsolutePath.toString)) {
        System.err.println(s"Warning: ${params.shared.dir} is not in your PATH")
        if (!coursier.bootstrap.LauncherBat.isWindows)
          System.err.println(
            s"""To fix that, add the following line to ${ShellUtil.rcFileOpt.getOrElse("your shell configuration file")}
               |
               |export PATH="$$PATH:${params.shared.dir.toAbsolutePath}"""".stripMargin
          )
      }
    }
  }

}
