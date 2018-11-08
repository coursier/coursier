package coursier.cli.publish

import coursier.cli.publish.fileset.FileSet
import coursier.cli.publish.upload.Upload
import coursier.maven.MavenRepository

abstract class PublishError(val message: String, cause: Throwable = null) extends Exception(message, cause)

object PublishError {

  final class InvalidArguments(message: String)
    extends PublishError(message)

  final class UnrecognizedRepositoryFormat(repo: String)
    extends PublishError(
      s"Unrecognized repository format: $repo (expected repository starting with / | ./ | http:// | https:// )"
    )

  final class NoInput
    extends PublishError("No input specified, e.g. via --dir or --sbt or --jar")

  final class UploadingError(repo: MavenRepository, errors: Seq[(FileSet.Path, Content, Upload.Error)])
    extends PublishError(
      errors
        .map {
          case (p, _, err) =>
            s"Error uploading ${p.repr} to ${repo.root}: ${err.getMessage}"
        }
        .mkString("\n")
    )

}
