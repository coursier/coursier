package coursier.jvm

import dataclass.{data, since}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipFile

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import coursier.cache.{ArchiveType, Cache, FileCache}
import coursier.cache.internal.FileUtil
import coursier.core.{Dependency, Repository}
import coursier.util.{Artifact, Task}
import coursier.util.Traverse.TraverseOps
import coursier.version.{Latest, Version, VersionParse}

import scala.collection.compat._
import scala.util.{Failure, Success, Try}

object JvmIndex {

  @deprecated("Use JvmChannel.handleAliases instead", "2.1.15")
  def handleAliases(indexName: String): String =
    JvmChannel.handleAliases(indexName)

  @deprecated("Use JvmChannel.gitHubIndexUrl instead", "2.1.15")
  def coursierIndexUrl: String =
    JvmChannel.gitHubIndexUrl

  @deprecated("Use JvmChannel.gitHubIndexUrl instead", "2.1.15")
  def defaultIndexUrl: String =
    JvmChannel.gitHubIndexUrl

  @deprecated("Use JvmChannel.centralModule instead", "2.1.15")
  def coursierIndexCoordinates: String =
    JvmChannel.centralModule().repr

  private def artifact(url: String) = Artifact(url).copy(changing = true)

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

  private[jvm] final case class IndexV2Entry(
    url: String,
    checksums: Option[Map[String, String]]
  )

  private val indexV2Codec =
    JsonCodecMaker.make[Map[String, Map[String, IndexV2Entry]]](CodecMakerConfig)

  /** Archive type of JVM archives listed in indices with checksums, that don't specify it */
  private def archiveTypeFromUrl(url: String): Option[String] = {
    val fileName = url.takeWhile(c => c != '?' && c != '#').split('/').last.toLowerCase(Locale.ROOT)
    if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) Some("tgz")
    else if (fileName.endsWith(".zip")) Some("zip")
    else if (fileName.endsWith(".tar.xz") || fileName.endsWith(".txz")) Some("txz")
    else if (fileName.endsWith(".tar.bz2") || fileName.endsWith(".tbz2")) Some("tbz2")
    else if (fileName.endsWith(".tar.zst") || fileName.endsWith(".tzst")) Some("tzst")
    else if (fileName.endsWith(".tar")) Some("tar")
    else None
  }

  /** Parses a per-OS and CPU architecture index with checksums
    *
    * These are the `indices-v2/$os-$arch.json` files of coursier/jvm-index, also published as
    * `coursier/jvm/indices/v2/$os-$arch.json` resources in the
    * `io.get-coursier.jvm.indices:index-$os-$arch` JARs on Maven Central.
    *
    * Their values are JSON objects, with a `url` field (archive URL) and a `checksums` one
    * (checksum type, like `sha256`, to hex-encoded checksum). Entries whose archive type can't be
    * deduced from their URL are ignored, as these couldn't be extracted anyway.
    */
  def fromV2Bytes(
    index: Array[Byte],
    os: String,
    arch: String
  ): Either[Throwable, JvmIndex] =
    Try(readFromArray(index)(indexV2Codec)) match {
      case Success(map) =>
        val content = map.map {
          case (name, versionMap) =>
            val versionMap0 = versionMap.flatMap {
              case (version, entry) =>
                archiveTypeFromUrl(entry.url).iterator.map(tpe => version -> s"$tpe+${entry.url}")
            }
            name -> versionMap0
        }
        val checksums = map
          .valuesIterator
          .flatMap(_.valuesIterator)
          .flatMap(entry => entry.checksums.filter(_.nonEmpty).map(entry.url -> _).iterator)
          .toMap
        Right(JvmIndex(Map(os -> Map(arch -> content)), Some(""), checksums))
      case Failure(t) => Left(t)
    }

  /** Parses an index, whichever its format
    *
    * Accepts the Jabba-like single index (`index.json` in coursier/jvm-index), the per-OS and CPU
    * architecture indices (`indices/$os-$arch.json`), and those same indices with checksums
    * (`indices-v2/$os-$arch.json`). `os` and `arch` are only used for the latter two, that don't
    * mention the OS and CPU architecture they're for.
    */
  def fromBytes(
    index: Array[Byte],
    os: String,
    arch: String
  ): Either[Throwable, JvmIndex] =
    fromBytes(index) match {
      case r @ Right(_) => r
      case Left(jabbaError) =>
        fromV2Bytes(index, os, arch) match {
          case r @ Right(_) => r
          case Left(v2Error) =>
            shortIndexFromBytes(index, os, arch) match {
              case r @ Right(_) => r
              case Left(v1Error) =>
                val ex = new Exception(
                  "Unrecognized JVM index format (tried the single index format, " +
                    "and the per-OS and CPU architecture formats with and without checksums)",
                  jabbaError
                )
                ex.addSuppressed(v2Error)
                ex.addSuppressed(v1Error)
                Left(ex)
            }
        }
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

    val osArchTask = Task.fromEither {
      val res =
        for {
          os0   <- os.map(Right(_)).getOrElse(JvmChannel.currentOs)
          arch0 <- arch.map(Right(_)).getOrElse(JvmChannel.currentArchitecture)
        } yield (os0, arch0)
      res.left.map(new Exception(_))
    }

    for {
      osArch <- osArchTask
      (os0, arch0) = osArch
      paths = Seq(
        (s"coursier/jvm/indices/v2/$os0-$arch0.json", IndexFormat.PerOsArchWithChecksums),
        (s"coursier/jvm/indices/v1/$os0-$arch0.json", IndexFormat.PerOsArch),
        ("index.json", IndexFormat.Single)
      )

      res <- coursier.Fetch(cache)
        .withDependencies(Seq(Dependency(channel.module, channel.versionConstraint)))
        .withRepositories(repositories)
        .ioResult

      _ = {
        for (logger <- cache.loggerOpt)
          logger.use {
            val retainedVersion =
              res.resolution.reconciledVersions
                .get(channel.module)
                .map(_.asString)
                .getOrElse("[unknown]")
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
                  case (path, format) =>
                    Option(zf.getEntry(path)).iterator.map((path, format, _))
                }
                .map {
                  case (path, format, e) =>
                    val binaryContent = FileUtil.readFully(zf.getInputStream(e))
                    val res = format match {
                      case IndexFormat.PerOsArchWithChecksums =>
                        fromV2Bytes(binaryContent, os0, arch0)
                      case IndexFormat.PerOsArch =>
                        shortIndexFromBytes(binaryContent, os0, arch0)
                      case IndexFormat.Single =>
                        fromBytes(binaryContent)
                    }
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
      new Exception(s"${paths.map(_._1).mkString(" or ")} not found in ${res.files.mkString(", ")}")
    }
  }

  private sealed abstract class IndexFormat extends Product with Serializable
  private object IndexFormat {
    case object Single                 extends IndexFormat
    case object PerOsArch              extends IndexFormat
    case object PerOsArchWithChecksums extends IndexFormat
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
            Task.fromEither {
              fromBytes(content, osOrDefault(os), archOrDefault(arch))
                .left.map(ex => new Exception(s"Error parsing ${f.path}", ex))
            }
        }
      case u: JvmChannel.FromUrl =>
        fromUrl(cache, u.url, os, arch)
      case m: JvmChannel.FromModule =>
        fromModule(cache, repositories, m, os, arch).flatMap {
          case Left(ex) => Task.fail(new Exception(s"No index found in ${m.repr}", ex))
          case Right(c) => Task.point(c)
        }
    }

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
    fromUrl(cache, indexUrl, None, None)

  private def osOrDefault(os: Option[String]): String =
    os.getOrElse(JvmChannel.defaultOsOrRaw())
  private def archOrDefault(arch: Option[String]): String =
    arch.getOrElse(JvmChannel.defaultArchitectureOrRaw())

  private def fromUrl(
    cache: Cache[Task],
    indexUrl: String,
    os: Option[String],
    arch: Option[String]
  ): Task[JvmIndex] =
    cache.file(artifact(indexUrl)).run.flatMap {
      case Left(err) =>
        Task.fail(new Exception(s"Error while getting $indexUrl: $err"))
      case Right(file) =>
        val contentTask = Task.delay {
          Files.readAllBytes(file.toPath)
        }
        contentTask.flatMap { content =>
          Task.fromEither {
            fromBytes(content, osOrDefault(os), archOrDefault(arch))
              .left.map(ex => new Exception(s"Error parsing $indexUrl", ex))
          }
        }
    }

  def load(
    cache: Cache[Task]
  ): Task[JvmIndex] =
    load(cache, coursier.Resolve().repositories, JvmChannel.default(), None, None)

  /** Loads the default index for the passed OS and CPU architecture */
  def load(
    cache: Cache[Task],
    os: String,
    arch: String
  ): Task[JvmIndex] =
    load(cache, coursier.Resolve().repositories, JvmChannel.default(os, arch), Some(os), Some(arch))

  def load(): Task[JvmIndex] =
    load(Cache.default, coursier.Resolve().repositories, JvmChannel.default(), None, None)

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

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class JvmIndex(
  content: Map[String, Map[String, Map[String, Map[String, String]]]],
  jdkNamePrefix: Option[String] = Some("jdk@"),
  /** Checksums of the archives listed in `content`, keyed by archive URL
    *
    * Values are maps from checksum type (like `sha256`) to hex-encoded checksum. Only indices with
    * checksums (see [[JvmIndex.fromV2Bytes]]) fill this.
    */
  @since
  checksums: Map[String, Map[String, String]] = Map.empty
) {

  import JvmIndex.parseDescriptor

  def filterIds(os: String, arch: String)(f: (String, String) => Boolean): JvmIndex =
    copy(
      content = content.map {
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
          val maybeConstraint = Some(VersionParse.versionConstraint(version))
            .filter(c => c.isValid && c.preferred.isEmpty)
            .orElse(
              Some(VersionParse.versionConstraint(version + "+"))
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
                JvmIndexEntry(
                  os,
                  arch,
                  name,
                  retainedVersion,
                  archiveType,
                  url,
                  checksums.getOrElse(url, Map.empty)
                )
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
