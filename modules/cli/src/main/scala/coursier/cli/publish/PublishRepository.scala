package coursier.cli.publish

import coursier.core.Authentication
import coursier.maven.MavenRepository

sealed abstract class PublishRepository extends Product with Serializable {
  def snapshotRepo: MavenRepository
  def releaseRepo: MavenRepository
  def readSnapshotRepo: MavenRepository
  def readReleaseRepo: MavenRepository

  final def repo(isSnapshot: Boolean): MavenRepository =
    if (isSnapshot)
      snapshotRepo
    else
      releaseRepo
  final def readRepo(isSnapshot: Boolean): MavenRepository =
    if (isSnapshot)
      readSnapshotRepo
    else
      readReleaseRepo
  def checkResultsRepo(isSnapshot: Boolean): MavenRepository =
    readRepo(isSnapshot)

  def withAuthentication(auth: Authentication): PublishRepository
}

object PublishRepository {

  final case class Simple(
    snapshotRepo: MavenRepository,
    readRepoOpt: Option[MavenRepository] = None
  ) extends PublishRepository {
    def releaseRepo: MavenRepository = snapshotRepo
    def readSnapshotRepo: MavenRepository = readRepoOpt.getOrElse(snapshotRepo)
    def readReleaseRepo: MavenRepository = readSnapshotRepo

    def withAuthentication(auth: Authentication): Simple =
      copy(
        snapshotRepo = snapshotRepo.copy(
          authentication = Some(auth)
        ),
        readRepoOpt = readRepoOpt
          .map(_.copy(authentication = Some(auth)))
      )
  }

  final case class Sonatype(base: MavenRepository) extends PublishRepository {

    def snapshotRepo: MavenRepository =
      base.copy(
        root = s"${base.root}/content/repositories/snapshots"
      )
    def releaseRepo: MavenRepository =
      base.copy(
        root = s"$restBase/staging/deploy/maven2"
      )
    def releaseRepoOf(repoId: String): MavenRepository =
      base.copy(
        root = s"$restBase/staging/deployByRepositoryId/$repoId"
      )
    def readSnapshotRepo: MavenRepository =
      snapshotRepo
    def readReleaseRepo: MavenRepository =
      base.copy(
        root = s"${base.root}/content/repositories/releases"
      )

    override def checkResultsRepo(isSnapshot: Boolean): MavenRepository =
      if (isSnapshot)
        super.checkResultsRepo(isSnapshot)
      else
        base.copy(
          root = s"${base.root}/content/repositories/public"
        )

    def restBase: String =
      s"${base.root}/service/local"

    def withAuthentication(auth: Authentication): Sonatype =
      copy(
        base = base.copy(
          authentication = Some(auth)
        )
      )
  }

  def gitHub(username: String, token: String): PublishRepository = {
    val repo = MavenRepository(
      s"https://maven.pkg.github.com/$username",
      authentication = Some(Authentication(username, token))
    )
    Simple(repo)
  }

  def bintray(user: String, repository: String, package0: String, apiKey: String): PublishRepository = {
    val repo = MavenRepository(
      s"https://api.bintray.com/maven/$user/$repository/$package0",
      authentication = Some(Authentication(user, apiKey))
    )
    val readRepo = MavenRepository(s"https://dl.bintray.com/$user/$repository") // allow to pass auth here?
    Simple(repo, Some(readRepo))
  }

}
