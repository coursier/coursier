package coursier.cli.channel

import caseapp.core.RemainingArgs
import coursier.cli.{CommandGroup, CoursierCommand}
import coursier.cli.Util.ValidatedExitOnError
import coursier.cli.params.OutputParams
import coursier.paths.Util.createDirectories

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object Channel extends CoursierCommand[ChannelOptions] {

  override def group: String = CommandGroup.channel

  def run(options: ChannelOptions, args: RemainingArgs): Unit = {
    val params = ChannelParam(options, args.all.nonEmpty).exitOnError()

    if (params.listChannels)
      displayChannels()
    else if (params.addChannel.nonEmpty)
      addChannel(params.addChannel, params.output)
  }

  def displayChannels() = {
    val configDir  = coursier.paths.CoursierPaths.defaultConfigDirectory()
    val channelDir = new File(configDir, "channels")

    for {
      files   <- Option(channelDir.listFiles())
      file    <- files
      rawLine <- new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).linesIterator
      line = rawLine.trim
      if line.nonEmpty
    } System.out.println(line)
  }

  def addChannel(channels: List[String], output: OutputParams) = {
    val configDir  = coursier.paths.CoursierPaths.defaultConfigDirectory()
    val channelDir = new File(configDir, "channels")

    // FIXME May not be fine with concurrency (two process doing this in parallel)
    val f = Iterator
      .from(1)
      .map { n =>
        new File(channelDir, s"channels-$n")
      }
      .filter(!_.exists())
      .next()

    if (output.verbosity >= 1) // todo : add output verbosity in options
      System.err.println(s"Writing $f")

    createDirectories(f.toPath.getParent)
    Files.write(
      f.toPath,
      channels.map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8)
    )
  }
}
