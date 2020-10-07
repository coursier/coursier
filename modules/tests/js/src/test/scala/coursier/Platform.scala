package coursier

import coursier.util.{EitherT, Task}

object Platform {

  val artifact: Repository.Fetch[Task] = { artifact =>
    EitherT(
      Task { implicit ec =>
        coursier.cache.internal.Platform.get(artifact.url)
          .map(Right(_))
          .recover { case e: Exception =>
          Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")"))
        }
      }
    )
  }

  def fetch(
    repositories: Seq[Repository]
  ): ResolutionProcess.Fetch[Task] =
    ResolutionProcess.fetch(repositories, artifact)

}
