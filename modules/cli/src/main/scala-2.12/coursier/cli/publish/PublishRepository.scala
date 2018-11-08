package coursier.cli.publish

import coursier.core.Authentication
import coursier.maven.MavenRepository

final case class PublishRepository(
  repo: MavenRepository,
  readRepoOpt: Option[MavenRepository] = None,
  releaseRepoOpt: Option[MavenRepository] = None,
  readReleaseRepoOpt: Option[MavenRepository] = None
) {
  def readRepo: MavenRepository =
    readRepoOpt.getOrElse(repo)
  def releaseRepo: MavenRepository =
    releaseRepoOpt.getOrElse(repo)
  def readReleaseRepo: MavenRepository =
    readReleaseRepoOpt.getOrElse(releaseRepo)

  def withAuthentication(auth: Authentication): PublishRepository =
    copy(
      repo = repo.copy(authentication = Some(auth)),
      readRepoOpt = readRepoOpt.map(_.copy(authentication = Some(auth))),
      releaseRepoOpt = releaseRepoOpt.map(_.copy(authentication = Some(auth))),
      readReleaseRepoOpt = readReleaseRepoOpt.map(_.copy(authentication = Some(auth)))
    )
  def withReadRepository(readRepo: MavenRepository): PublishRepository =
    copy(
      readRepoOpt = Some(readRepo),
      readReleaseRepoOpt = Some(readRepo)
    )
}
