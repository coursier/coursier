package coursier.cli.install

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.Locale

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.cache.Cache
import coursier.cli.util.Guard
import coursier.core.Repository
import coursier.install.{AppDescriptor, AppGenerator, Channel, Channels, RawAppDescriptor, RawSource, Source}
import coursier.ivy.IvyRepository
import coursier.launcher.internal.Windows
import coursier.maven.MavenRepository
import coursier.util.{Sync, Task}

object Install extends CaseApp[InstallOptions] {

  def appDescriptor(
    channels: Seq[Channel],
    repositories: Seq[Repository],
    cache: Cache[Task],
    id: String,
    verbosity: Int = 0
  ): Either[String, (Source, Array[Byte], AppDescriptor)] =
    for {

      t0 <- Channels.find(channels, id, cache, repositories).toRight {
        s"Cannot find app $id in channels ${channels.map(_.repr).mkString(", ")}"
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

    val fromArgs =
      Some(params.rawAppDescriptor)
        .filter(!_.isEmpty)
        .map { desc =>
          desc.appDescriptor.toEither match {
            case Left(errors) =>
              for (err <- errors.toList)
                System.err.println(err)
              sys.exit(1)
            case Right(d) => (None, (desc.repr.getBytes(StandardCharsets.UTF_8), d))
          }
        }

    val fromIds = args.all.map { id =>

      if (params.channels.isEmpty) {
        System.err.println(s"Error: app id specified, but no channels passed")
        sys.exit(1)
      } else if (params.rawAppDescriptor.withRepositories(Nil).isEmpty) {

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
            val repositories = params.repositories.toList.flatMap {
              case m: MavenRepository =>
                // FIXME This discard authentication, …
                List(m.root)
              case i: IvyRepository =>
                // FIXME This discard authentication, metadataPattern, …
                List(s"ivy:${i.pattern.string}")
              case _ =>
                // ???
                Nil
            }
            val rawSource = RawSource(repositories, source.channel.repr, id)
            (Some((rawSource, source.withId(id))), (repr, overrideVersionOpt.fold(desc)(desc.overrideVersion)))
        }
      } else {
        import caseapp.core.util.NameOps._
        val argNames = InstallAppOptions.help.args.filter(_.name.name != "repository").map(_.name.option)
        System.err.println(
          s"App description arguments (${argNames.mkString(", ")}) can only be specified along with standard dependencies, not with app ids"
        )
        sys.exit(1)
      }
    }

    if (params.installChannels.nonEmpty) {
      val configDir = coursier.paths.CoursierPaths.configDirectory()
      val channelDir = new File(configDir, "channels")

      // FIXME May not be fine with concurrency (two process doing this in parallel)
      val f = Stream.from(1)
        .map { n =>
          new File(channelDir, s"channels-$n")
        }
        .filter(!_.exists())
        .head

      if (params.shared.verbosity >= 1)
        System.err.println(s"Writing $f")
      Files.createDirectories(f.toPath.getParent)
      Files.write(f.toPath, params.installChannels.map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8))
    }

    if (fromArgs.isEmpty && fromIds.isEmpty) {
      if (params.shared.verbosity >= 0 && params.installChannels.isEmpty)
        System.err.println("Nothing to install")
      sys.exit(0)
    }

    for ((sourceOpt, (b, appDescriptor)) <- fromArgs.iterator ++ fromIds) {

      val name = params.nameOpt
        .orElse(appDescriptor.nameOpt)
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
        Some((appDescriptor, b)),
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
    }

    if (params.shared.verbosity >= 0) {
      val path = Option(System.getenv("PATH"))
        .toSeq
        .flatMap(_.split(File.pathSeparatorChar).toSeq)
        .toSet

      if (!path(params.shared.dir.toAbsolutePath.toString)) {
        System.err.println(s"Warning: ${params.shared.dir} is not in your PATH")
        if (!Windows.isWindows)
          System.err.println(
            s"""To fix that, add the following line to ${ShellUtil.rcFileOpt.getOrElse("your shell configuration file")}
               |
               |export PATH="$$PATH:${params.shared.dir.toAbsolutePath}"""".stripMargin
          )
      }
    }
  }

}
