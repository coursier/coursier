package coursier.cli.publish.signing

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}

import coursier.cli.publish.Content
import coursier.util.Task

import scala.collection.JavaConverters._

final case class GpgSigner(
  key: String,
  command: String = "gpg",
  extraOptions: Seq[String] = Nil
) extends Signer {

  def sign(content: Content): Task[Either[String, String]] = {

    val pathTemporaryTask = content.pathOpt.map(p => Task.point((p, false))).getOrElse {
      val p = Files.createTempFile(
        "signer",
        ".content",
        PosixFilePermissions.asFileAttribute(
          Set(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
          ).asJava
        )
      )
      content.contentTask.map { b =>
        Files.write(p, b)
        (p, true)
      }
    }

    pathTemporaryTask.flatMap {
      case (path, temporary) =>
        sign0(path, temporary, content)
    }
  }

  private def sign0(path: Path, temporary: Boolean, content: Content): Task[Either[String, String]] =
    Task.delay {

      // inspired by https://github.com/jodersky/sbt-gpg/blob/853e608120eac830068bbb121b486b7cf06fc4b9/src/main/scala/Gpg.scala

      val dest = Files.createTempFile(
        "signer",
        ".asc",
        PosixFilePermissions.asFileAttribute(
          Set(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
          ).asJava
        )
      )

      try {
        val pb = new ProcessBuilder()
          .command(
            Seq(command) ++
              extraOptions ++
              Seq(
                "--local-user", key,
                "--armor",
                "--yes",
                "--output", dest.toAbsolutePath.toString,
                "--detach-sign",
                path.toAbsolutePath.toString
              ): _*
          )
          .inheritIO()

        val p = pb.start()

        val retCode = p.waitFor()

        if (retCode == 0)
          Right(new String(Files.readAllBytes(dest), StandardCharsets.UTF_8))
        else
          Left(s"gpg failed (return code: $retCode)")
      } finally {
        // Ignore I/O errors?
        Files.deleteIfExists(dest)
        if (temporary)
          Files.deleteIfExists(path)
      }
    }
}
