package coursier.cli.util

import java.io.File
import java.nio.file.Path
import scala.scalanative.{build => sn}

object Native {
  def create(
    mainClass: String,
    files: Seq[File],
    output0: File,
    wd: File,
    log: String => Unit = s => Console.err.println(s),
    verbosity: Int = 0,
    config: sn.Config
  ): Unit = {
    val classpath: Seq[Path] = files.map(_.toPath)
    val workdir: Path        = wd.toPath
    val main: String         = mainClass + "$"
    val outpath              = output0.toPath

    val config0 = config
      .withMainClass(main)
      .withClassPath(classpath)
      .withWorkdir(workdir)

    sn.Build.build(config0, outpath)
  }

  private def linkingOptions(): Seq[String] = sn.Discover.linkingOptions() ++
    sys.env.get("LDFLAGS").toSeq.flatMap(_.split("\\s+"))

  def deleteRecursive(f: File): Unit = {
    if (f.isDirectory) {
      f.listFiles().foreach(deleteRecursive)
    }
    f.delete()
  }
}
