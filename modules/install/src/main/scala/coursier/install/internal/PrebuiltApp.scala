package coursier.install.internal

import coursier.cache.{ArchiveCache, ArchiveType, ArtifactError, Cache}
import coursier.cache.loggers.ProgressBarRefreshDisplay
import coursier.install.{AppDescriptor, InstallDir}
import coursier.install.error.DownloadError
import coursier.util.{Artifact, Task}

import java.io.File
import java.nio.file.Files

sealed abstract class PrebuiltApp extends Product with Serializable {
  def artifact: Artifact
  def file: File
}

object PrebuiltApp {

  final case class Uncompressed(
    artifact: Artifact,
    file: File
  ) extends PrebuiltApp

  final case class Compressed(
    artifact: Artifact,
    file: File,
    archiveType: ArchiveType,
    pathInArchiveOpt: Option[String]
  ) extends PrebuiltApp

  final case class ExtractedArchive(
    artifact: Artifact,
    archiveRoot: File,
    pathInArchive: String,
    file: File
  ) extends PrebuiltApp

  def get(
    desc: AppDescriptor,
    cache: Cache[Task],
    archiveCache: ArchiveCache[Task],
    verbosity: Int,
    platform: Option[String],
    platformExtensions: Seq[String],
    preferPrebuilt: Boolean
  ): Either[Seq[String], PrebuiltApp] = {

    def downloadArtifacts(
      artifacts: Seq[(Artifact, Option[(ArchiveType, Option[String])])]
    ): Iterator[PrebuiltApp] =
      artifacts.iterator.flatMap {
        case (artifact, archiveTypeOpt) =>
          if (verbosity >= 2)
            System.err.println(s"Checking prebuilt launcher at ${artifact.url}")
          def maybeFileIt: Iterator[File] = {
            cache.loggerOpt.foreach(_.init())
            val maybeFile =
              try cache.file(artifact).run.unsafeRun()(cache.ec)
              finally cache.loggerOpt.foreach(_.stop())
            handleArtifactErrors(maybeFile, artifact, verbosity)
              .iterator
          }
          def maybeExtractedArchiveIt: Iterator[File] = {
            cache.loggerOpt.foreach(_.init())
            val maybeDir =
              try archiveCache.get(artifact).unsafeRun()(cache.ec)
              finally cache.loggerOpt.foreach(_.stop())
            handleArtifactErrors(maybeDir, artifact, verbosity)
              .iterator
          }
          archiveTypeOpt match {
            case None =>
              maybeFileIt.map { f =>
                Uncompressed(artifact, f)
              }
            case Some((archiveType, None)) =>
              maybeFileIt.map { f =>
                Compressed(
                  artifact,
                  f,
                  archiveType,
                  None
                )
              }
            case Some((archiveType, Some(pathInArchive))) =>
              maybeExtractedArchiveIt.flatMap { f =>
                val innerF = new File(f, pathInArchive)
                if (innerF.exists())
                  Iterator.single(ExtractedArchive(
                    artifact,
                    f,
                    pathInArchive,
                    innerF
                  ))
                else
                  Iterator.empty
              }
          }
      }

    candidatePrebuiltArtifacts(
      desc,
      cache,
      verbosity,
      platform,
      platformExtensions,
      preferPrebuilt
    ).toRight(Nil).flatMap { artifacts =>
      val iterator = downloadArtifacts(artifacts)
      if (iterator.hasNext) Right(iterator.next())
      else Left(artifacts.map(_._1.url))
    }
  }

  private[coursier] def handleArtifactErrors(
    maybeFile: Either[ArtifactError, File],
    artifact: Artifact,
    verbosity: Int
  ): Option[File] =
    maybeFile match {
      case Left(e: ArtifactError.NotFound) =>
        if (verbosity >= 2)
          System.err.println(s"No prebuilt launcher found at ${artifact.url}")
        None
      case Left(e: ArtifactError.DownloadError)
          if e.getCause.isInstanceOf[javax.net.ssl.SSLHandshakeException] =>
        // These seem to happen on Windows for non existing artifacts, only from the native launcher apparently???
        // Interpreting these errors as not-found-errors too.
        if (verbosity >= 2)
          System.err.println(
            s"No prebuilt launcher found at ${artifact.url} (SSL handshake exception)"
          )
        None
      case Left(e) =>
        // FIXME Ignore some other kind of errors too? Just warn about them?
        throw new DownloadError(artifact.url, e)
      case Right(f) =>
        if (verbosity >= 1) {
          val size = ProgressBarRefreshDisplay.byteCount(Files.size(f.toPath))
          System.err.println(s"Found prebuilt launcher at ${artifact.url} ($size)")
        }
        Some(f)
    }

  private def candidatePrebuiltArtifacts(
    desc: AppDescriptor,
    cache: Cache[Task],
    verbosity: Int,
    platform: Option[String],
    platformExtensions: Seq[String],
    preferPrebuilt: Boolean
  ): Option[Seq[(Artifact, Option[(ArchiveType, Option[String])])]] = {

    def mainVersionsIterator(): Iterator[String] = {
      val it0 = desc.candidateMainVersions(cache, verbosity)
      val it =
        if (it0.hasNext) it0
        else desc.mainVersionOpt.iterator
      // check the latest 5 versions if preferPrebuilt is true
      // FIXME Don't hardcode that number?
      it.take(if (preferPrebuilt) 5 else 1)
    }

    def urlArchiveType(url: String): (String, Option[(ArchiveType, Option[String])]) = {
      val idx = url.indexOf('+')
      if (idx < 0) (url, None)
      else
        ArchiveType.parse(url.take(idx)) match {
          case Some(tpe) =>
            val url0         = url.drop(idx + 1)
            val subPathIndex = url0.indexOf('!')
            if (subPathIndex < 0)
              (url0, Some((tpe, None)))
            else {
              val subPath = url0.drop(subPathIndex + 1)
              (url0.take(subPathIndex), Some((tpe, Some(subPath))))
            }
          case None =>
            (url, None)
        }
    }

    def patternArtifacts(
      pattern: String
    ): Seq[(Artifact, Option[(ArchiveType, Option[String])])] = {

      val artifactsIt = for {
        version <- mainVersionsIterator()
        isSnapshot = version.endsWith("SNAPSHOT")
        baseUrl0 = pattern
          .replace("${version}", version)
          .replace("${platform}", platform.getOrElse(""))
        (baseUrl, archiveTypeAndPathOpt) = urlArchiveType(baseUrl0)
        ext <-
          if (archiveTypeAndPathOpt.forall(_._2.nonEmpty))
            platformExtensions.iterator ++ Iterator("")
          else Iterator("")
        (url, archiveTypeAndPathOpt0) = archiveTypeAndPathOpt match {
          case None                  => (baseUrl + ext, archiveTypeAndPathOpt)
          case Some((tpe, subPath0)) => (baseUrl, Some((tpe, subPath0.map(_ + ext))))
        }
      } yield (Artifact(url).withChanging(isSnapshot), archiveTypeAndPathOpt0)

      artifactsIt.toVector
    }

    if (desc.launcherType.isNative)
      desc.prebuiltLauncher
        .orElse(desc.prebuiltBinaries.get(platform.getOrElse("")))
        .map(patternArtifacts)
    else
      None
  }

}
