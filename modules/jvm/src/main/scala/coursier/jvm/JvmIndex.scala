package coursier.jvm

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipFile

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import coursier.cache.{ArchiveType, Cache, FileCache}
import coursier.cache.internal.FileUtil
import coursier.core.{Dependency, Latest, Parse, Repository, Version}
import coursier.util.{Artifact, Task}
import coursier.util.Traverse.TraverseOps
import dataclass.data

import scala.collection.compat._
import scala.util.{Failure, Success, Try}

object JvmIndex {

  @deprecated("Use JvmChannel.handleAliases instead", "2.1.15")
  def handleAliases(indexName: String): String =
    JvmChannel.handleAliases(indexName)

  @deprecated("Use JvmChannel.coursierIndexUrl instead", "2.1.15")
  def coursierIndexUrl: String =
    JvmChannel.gitHubIndexUrl

  @deprecated("Use JvmChannel.coursierIndexUrl instead", "2.1.15")
  def defaultIndexUrl: String =
    JvmChannel.gitHubIndexUrl

  @deprecated("Use JvmChannel.centralModule instead", "2.1.15")
  def coursierIndexCoordinates: String =
    JvmChannel.centralModule().repr

  private def artifact(url: String) = Artifact(url).withChanging(true)

  private val codec = JsonCodecMaker.make[Map[
    String,
    Map[String, Map[String, Map[String, String]]]
  ]](CodecMakerConfig)

  def fromBytes(index: Array[Byte]): Either[Throwable, JvmIndex] =
    Try(readFromArray(index)(codec)) match {
      case Success(map) => Right(JvmIndex(map))
      case Failure(t)   => Left(t)
    }

  private val shortIndexCodec =
    JsonCodecMaker.make[Map[String, Map[String, String]]](CodecMakerConfig)

  private def shortIndexFromBytes(
    index: Array[Byte],
    os: String,
    arch: String
  ): Either[Throwable, JvmIndex] =
    Try(readFromArray(index)(shortIndexCodec)) match {
      case Success(map) => Right(JvmIndex(Map(os -> Map(arch -> map)), jdkNamePrefix = Some("")))
      case Failure(t)   => Left(t)
    }

  def fromString(index: String): Either[Throwable, JvmIndex] =
    Try(readFromString(index)(codec)) match {
      case Success(map) => Right(JvmIndex(map))
      case Failure(t)   => Left(t)
    }

  private def withZipFile[T](file: File)(f: ZipFile => T): T = {
    var zf: ZipFile = null
    try {
      zf = new ZipFile(file)
      f(zf)
    }
    finally if (zf != null)
        zf.close()
  }

  private def fromModule(
    cache: Cache[Task],
    repositories: Seq[Repository],
    channel: JvmChannel.FromModule,
    os: Option[String],
    arch: Option[String]
  ): Task[Either[Exception, JvmIndex]] = {

    val os0   = os.getOrElse(JvmChannel.defaultOs())
    val arch0 = arch.getOrElse(JvmChannel.defaultArchitecture())
    val paths = Seq(
      (s"coursier/jvm/indices/v1/$os0-$arch0.json", true),
      ("index.json", false)
    )

    for {
      res <- coursier.Fetch(cache)
        .withDependencies(Seq(Dependency(channel.module, channel.version)))
        .withRepositories(repositories)
        .ioResult

      _ = {
        for (logger <- cache.loggerOpt)
          logger.use {
            val retainedVersion =
              res.resolution.reconciledVersions.getOrElse(channel.module, "[unknown]")
            logger.pickedModuleVersion(channel.module.repr, retainedVersion)
          }
      }

      dataOpt <- res
        .files
        .iterator
        .map { f =>
          Task.delay {
            withZipFile(f) { zf =>
              paths
                .iterator
                .flatMap {
                  case (path, isShort) =>
                    Option(zf.getEntry(path)).iterator.map((path, isShort, _))
                }
                .map {
                  case (path, isShort, e) =>
                    val binaryContent = FileUtil.readFully(zf.getInputStream(e))
                    val res =
                      if (isShort) shortIndexFromBytes(binaryContent, os0, arch0)
                      else fromBytes(binaryContent)
                    res.left.map(ex => new Exception(s"Error parsing $f!$path", ex))
                }
                .find(_ => true)
            }
          }.flatMap {
            case None             => Task.point(None)
            case Some(Right(idx)) => Task.point(Some(idx))
            case Some(Left(ex))   => Task.fail(ex)
          }
        }
        .foldLeft[Task[Option[JvmIndex]]](Task.point(None)) { (acc, elem) =>
          acc.flatMap {
            case None        => elem
            case s @ Some(_) => Task.point(s)
          }
        }
    } yield dataOpt.toRight {
      new Exception(s"${paths.mkString(" or ")} not found in ${res.files.mkString(", ")}")
    }
  }

  def load(
    cache: Cache[Task],
    repositories: Seq[Repository],
    indexChannel: JvmChannel,
    os: Option[String],
    arch: Option[String]
  ): Task[JvmIndex] =
    indexChannel match {
      case f: JvmChannel.FromFile =>
        Task.delay(Files.readAllBytes(f.path)).attempt.flatMap {
          case Left(ex) => Task.fail(new Exception(s"Error while reading ${f.path}", ex))
          case Right(content) =>
            Task.fromEither(fromBytes(content))
        }
      case u: JvmChannel.FromUrl =>
        cache.file(artifact(u.url)).run.flatMap {
          case Left(err) =>
            Task.fail(new Exception(s"Error while getting ${u.url}: $err"))
          case Right(file) =>
            val contentTask = Task.delay {
              Files.readAllBytes(file.toPath)
            }
            contentTask.flatMap { content =>
              Task.fromEither(fromBytes(content))
            }
        }
      case m: JvmChannel.FromModule =>
        fromModule(cache, repositories, m, os, arch).flatMap {
          case Left(ex) => Task.fail(new Exception(s"No index found in ${m.repr}", ex))
          case Right(c) => Task.point(c)
        }
    }

  @deprecated("Use the override accepting os and arch", "2.1.15")
  def load(
    cache: Cache[Task],
    repositories: Seq[Repository],
    indexChannel: JvmChannel
  ): Task[JvmIndex] =
    load(cache, repositories, indexChannel, None, None)

  def load(
    cache: Cache[Task],
    indexUrl: String
  ): Task[JvmIndex] =
    cache.file(artifact(indexUrl)).run.flatMap {
      case Left(err) =>
        Task.fail(new Exception(s"Error while getting $indexUrl: $err"))
      case Right(file) =>
        val contentTask = Task.delay {
          Files.readAllBytes(file.toPath)
        }
        contentTask.flatMap { content =>
          Task.fromEither(fromBytes(content))
        }
    }

  def load(
    cache: Cache[Task]
  ): Task[JvmIndex] =
    load(cache, coursier.Resolve().repositories, JvmChannel.default(), None, None)

  def load(): Task[JvmIndex] =
    load(FileCache(), coursier.Resolve().repositories, JvmChannel.default(), None, None)

  @deprecated("Use JvmChannel.currentOs instead", "2.1.15")
  def currentOs: Either[String, String] =
    JvmChannel.currentOs

  @deprecated("Use JvmChannel.currentArchitecture instead", "2.1.15")
  def currentArchitecture: Either[String, String] =
    JvmChannel.currentArchitecture

  @deprecated("Use JvmChannel.defaultOs instead", "2.1.15")
  def defaultOs(): String =
    JvmChannel.defaultOs()

  @deprecated("Use JvmChannel.defaultArchitecture instead", "2.1.15")
  def defaultArchitecture(): String =
    JvmChannel.defaultArchitecture()

  private def parseDescriptor(input: String): Either[String, (ArchiveType, String)] = {
    val idx = input.indexOf('+')
    if (idx < 0)
      Left(s"Malformed url descriptor '$input'")
    else {
      val archiveTypeStr = input.take(idx)
      val url            = input.drop(idx + 1)
      ArchiveType.parse(archiveTypeStr)
        .map((_, url))
        .toRight(s"Unrecognized archive type '$archiveTypeStr'")
    }
  }
}

@data class JvmIndex(
  content: Map[String, Map[String, Map[String, Map[String, String]]]],
  jdkNamePrefix: Option[String] = Some("jdk@")
) {

  import JvmIndex.parseDescriptor

  def filterIds(os: String, arch: String)(f: (String, String) => Boolean): JvmIndex =
    withContent(
      content.map {
        case (`os`, osIndex) =>
          os -> osIndex.map {
            case (`arch`, archIndex) =>
              val updated = archIndex
                .map {
                  case (name, versionMap) =>
                    name -> versionMap.view.filterKeys(v => f(name, v)).toMap
                }
                .filter(_._2.nonEmpty)
              arch -> updated
            case other => other
          }
        case other => other
      }
    )

  def lookup(
    name: String,
    version: String,
    os: Option[String] = None,
    arch: Option[String] = None
  ): Either[String, Seq[JvmIndexEntry]] = {

    def fromVersionConstraint(
      versionIndex: Map[String, String],
      version: String
    ): Either[String, Seq[(String, String)]] =
      Latest(version) match {
        case Some(_) =>
          // TODO Filter versions depending on latest kind
          Right(versionIndex.toVector.sortBy { case (v, _) => Version(v) })
        case None =>
          val maybeConstraint = Some(Parse.versionConstraint(version))
            .filter(c => c.isValid && c.preferred.isEmpty)
            .orElse(
              Some(Parse.versionConstraint(version + "+"))
                .filter(c => c.isValid && c.preferred.isEmpty)
            )
            .toRight(s"Invalid version constraint '$version'")

          maybeConstraint.flatMap { c =>
            assert(c.preferred.isEmpty)
            val inInterval = versionIndex.view.filterKeys(s => c.interval.contains(Version(s)))
            if (inInterval.isEmpty)
              Left(s"No $name version matching '$version' found")
            else {
              val preferredInInterval = inInterval.filterKeys(s => c.preferred.contains(Version(s)))
              val map =
                if (preferredInInterval.isEmpty) inInterval
                else preferredInInterval
              val retained = map.toVector.sortBy { case (v, _) => Version(v) }
              Right(retained)
            }
          }

      }

    for {
      os        <- os.map(Right(_)).getOrElse(JvmChannel.currentOs)
      arch      <- arch.map(Right(_)).getOrElse(JvmChannel.currentArchitecture)
      osIndex   <- content.get(os).toRight(s"No JVM found for OS $os")
      archIndex <- osIndex.get(arch).toRight(s"No JVM found for OS $os and CPU architecture $arch")
      versionIndex <-
        archIndex.get(jdkNamePrefix.getOrElse("") + name).toRight(s"JVM $name not found")
      needs1Prefix = versionIndex.keysIterator.forall(_.startsWith("1."))
      version0 =
        if (needs1Prefix)
          if (version.startsWith("1.") || version == "1" || version == "1+")
            version
          else
            "1." + version
        else
          version
      retainedVersionUrlDescriptor <- versionIndex
        .get(version0)
        .map(url => Right(Seq((version0, url))))
        .getOrElse(fromVersionConstraint(versionIndex, version0))
      // (retainedVersion, urlDescriptor) = retainedVersionUrlDescriptor
      entries <- retainedVersionUrlDescriptor
        .eitherTraverse {
          case (retainedVersion, urlDescriptor) =>
            parseDescriptor(urlDescriptor).map {
              case (archiveType, url) =>
                JvmIndexEntry(os, arch, name, retainedVersion, archiveType, url)
            }
        }
    } yield entries
  }

  def available(
    os: Option[String] = None,
    arch: Option[String] = None,
    jdkNamePrefix: Option[String] = Some("jdk@")
  ): Either[String, Map[String, Map[String, String]]] =
    for {
      os        <- os.map(Right(_)).getOrElse(JvmChannel.currentOs)
      arch      <- arch.map(Right(_)).getOrElse(JvmChannel.currentArchitecture)
      osIndex   <- content.get(os).toRight(s"No JVM found for OS $os")
      archIndex <- osIndex.get(arch).toRight(s"No JVM found for OS $os and CPU architecture $arch")
    } yield archIndex.map {
      case (name, versionMap) =>
        val name0 = jdkNamePrefix.fold(name)(name.stripPrefix)
        name0 -> versionMap
    }
}
