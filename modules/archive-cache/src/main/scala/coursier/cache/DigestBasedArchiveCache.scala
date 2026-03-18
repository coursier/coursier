package coursier.cache

import coursier.util.Sync
import dataclass.data

import java.io.File

@data class DigestBasedArchiveCache[F[_]](
  archiveCache: ArchiveCache[F]
)(implicit
  sync: Sync[F]
) {
  private def S = sync
  def get(artifact: DigestArtifact) =
    archiveCache.get0(
      new File(
        archiveCache.location,
        s"digest/${artifact.digest.take(2)}/${artifact.digest.drop(2)}"
      ),
      None,
      S.point(Right(artifact.path.toFile))
    )
}
