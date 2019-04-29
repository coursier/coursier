package coursier.internal

import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest

import coursier.cache.CacheLocks
import coursier.core.{Classifier, Dependency, Repository, Type}
import coursier.params.ResolutionParams
import coursier.paths.CachePath

final case class FetchCache(base: Path) {

  def dir(key: FetchCache.Key): Path =
    base.resolve(s"${key.sha1.take(2)}/${key.sha1.drop(2)}")
  def resultFile(key: FetchCache.Key): Path =
    dir(key).resolve("artifacts")
  def lockFile(key: FetchCache.Key): Path =
    dir(key).resolve("lock")

  def read(key: FetchCache.Key): Option[Seq[File]] = {
    val resultFile0 = resultFile(key)
    if (Files.isRegularFile(resultFile0)) {
      val artifacts = Predef.augmentString(new String(Files.readAllBytes(resultFile0), StandardCharsets.UTF_8))
        .lines
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(Paths.get(_))
        .toVector

      if (artifacts.forall(Files.isRegularFile(_)))
        Some(artifacts.map(_.toFile))
      else
        None
    } else
      None
  }

  def write(key: FetchCache.Key, artifacts: Seq[File]): Boolean = {
    val resultFile0 = resultFile(key)
    val tmpFile = CachePath.temporaryFile(resultFile0.toFile).toPath

    def doWrite(): Unit = {
      Files.write(tmpFile, artifacts.map(_.getAbsolutePath).mkString("\n").getBytes(StandardCharsets.UTF_8))
      Files.move(tmpFile, resultFile0, StandardCopyOption.ATOMIC_MOVE)
    }

    CacheLocks.withLockOr(
      base.toFile,
      resultFile0.toFile
    )(
      { doWrite(); true },
      Some(false)
    )
  }

}

object FetchCache {

  private[coursier] final case class Key(
    dependencies: Seq[Dependency],
    repositories: Seq[Repository],
    resolutionParams: ResolutionParams,

    // these 4 come from ResolutionParams, but are ordered here
    forceVersion: Seq[(coursier.core.Module, String)],
    properties: Seq[(String, String)],
    forcedProperties: Seq[(String, String)],
    profiles: Seq[String],

    cacheLocation: String,
    classifiers: Seq[Classifier],
    mainArtifacts: Option[Boolean],
    artifactTypesOpt: Option[Seq[Type]]
  ) {
    lazy val repr: String =
      productIterator.mkString("(", ", ", ")")
    lazy val sha1: String = {
      val md = MessageDigest.getInstance("SHA-1")
      val b = md.digest(repr.getBytes(StandardCharsets.UTF_8))
      val s = new BigInteger(1, b).toString(16)
      ("0" * (40 - s.length)) + s
    }
  }

}
