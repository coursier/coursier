package coursier.cli.channel

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.cli.Util.ValidatedExitOnError
import java.io.File
import java.nio.file.Files
import coursier.paths.Util
import java.nio.charset.StandardCharsets
import java.io.FileReader
import coursier.cli.params.OutputParams

object ChannelCommand extends CaseApp[ChannelOptions] {

  override def run(
      options: ChannelOptions,
      args: RemainingArgs
  ): Unit = {
    val params = ChannelParam(options, args.all.nonEmpty).exitOnError()

    if (params.listChannels) {
      displayChannels()
    } else if (params.addChannel.nonEmpty)
      addChannel(params.addChannel, params.output)
  }

  def displayChannels() = {
    val configDir = coursier.paths.CoursierPaths.defaultConfigDirectory()
    val channelDir = new File(configDir, "channels")

    Option(channelDir.listFiles())
      .map(_.foreach { f =>
        val channel = Files.readString(f.toPath())
        System.out.println(channel)
      })
  }

  def addChannel(channels: List[String], output: OutputParams) = {
    val configDir = coursier.paths.CoursierPaths.defaultConfigDirectory()
    val channelDir = new File(configDir, "channels")

    // FIXME May not be fine with concurrency (two process doing this in parallel)
    val f = Stream
      .from(1)
      .map { n =>
        new File(channelDir, s"channels-$n")
      }
      .filter(!_.exists())
      .head

    if (output.verbosity >= 1) // todo : add output verbosity in options
      System.err.println(s"Writing $f")

    Util.createDirectories(f.toPath.getParent)
    Files.write(
      f.toPath,
      channels.map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8)
    )
  }
}
