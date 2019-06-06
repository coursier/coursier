package coursier.cli.publish.params

import java.nio.file.{Files, Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.publish.dir.Dir
import coursier.cli.publish.options.DirectoryOptions
import coursier.publish.sbt.Sbt

final case class DirectoryParams(
  directories: Seq[Path],
  sbtDirectories: Seq[Path]
)

object DirectoryParams {
  def apply(options: DirectoryOptions, args: Seq[String]): ValidatedNel[String, DirectoryParams] = {

    val dirsV = options.dir.traverse { d =>
      val dir0 = Paths.get(d)
      if (Files.exists(dir0)) {
        if (Files.isDirectory(dir0))
          Validated.validNel(dir0)
        else
          Validated.invalidNel(s"$d not a directory")
      } else
        Validated.invalidNel(s"$d not found")
    }

    val sbtDirsV = ((if (options.sbt) List(".") else Nil) ::: options.sbtDir).traverse { d =>
      val dir0 = Paths.get(d)
      if (Files.exists(dir0)) {
        if (Files.isDirectory(dir0)) {
          val buildProps = dir0.resolve("project/build.properties")
          if (Files.exists(buildProps))
            Validated.validNel(dir0)
          else
            Validated.invalidNel(s"project/build.properties not found under sbt directory $d")
        } else
          Validated.invalidNel(s"$d not a directory")
      } else
        Validated.invalidNel(s"$d not found")
    }

    val extraV = args
      .toList
      .traverse { a =>
        val p = Paths.get(a)
        if (Sbt.isSbtProject(p))
          Validated.validNel((None, Some(p)))
        else if (Dir.isRepository(p))
          Validated.validNel((Some(p), None))
        else
          Validated.invalidNel(s"$a is neither an sbt project or a local repository")
      }

    (dirsV, sbtDirsV, extraV).mapN {
      case (dirs, sbtDirs, extra) =>
        DirectoryParams(
          dirs ++ extra.flatMap(_._1),
          sbtDirs ++ extra.flatMap(_._2)
        )
    }
  }
}
