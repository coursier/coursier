package coursier.jvm

import java.util.Locale

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import coursier.cache.{Cache, FileCache}
import coursier.core.{Latest, Parse, Version}
import coursier.util.{Artifact, Task}
import dataclass.data

import scala.util.{Failure, Success, Try}

object JvmIndex {

  def defaultIndexUrl: String =
    "https://github.com/shyiko/jabba/raw/master/index.json"

  private def artifact(url: String) = Artifact(url).withChanging(true)

  private val codec = JsonCodecMaker.make[Map[String, Map[String, Map[String, Map[String, String]]]]](CodecMakerConfig)

  def fromString(index: String): Either[Throwable, JvmIndex] =
    Try(readFromString(index)(codec)) match {
      case Success(map) => Right(JvmIndex(map))
      case Failure(t) => Left(t)
    }

  def load(
    cache: Cache[Task],
    indexUrl: String
  ): Task[JvmIndex] =
    cache.fetch(artifact(indexUrl)).run.flatMap {
      case Left(err) =>
        Task.fail(new Exception(s"Error while getting $indexUrl: $err"))
      case Right(content) =>
        Task.fromEither(fromString(content))
    }

  def jabbaIndexGraalvmJava8Hack(index: JvmIndex): JvmIndex =
    index.withContent(
      index.content.map {
        case (os, m1) =>
          os -> m1.map {
            case (arch, m2) =>
              arch -> m2.flatMap {
                case (jdkName @ "jdk@graalvm", m3) =>
                  val jdk8 = jdkName -> m3.map {
                    case (version, url) =>
                      version -> url.replaceAllLiterally("-java11-", "-java8-")
                  }
                  val jdk11 = s"$jdkName-java11" -> m3.collect {
                    case (version, url) if url.contains("-java8-") || url.contains("-java11-") =>
                      version -> url.replaceAllLiterally("-java8-", "-java11-")
                  }
                  Seq(jdk8, jdk11).filter(_._2.nonEmpty)
                case (jdkName, m3) =>
                  Seq(jdkName -> m3)
              }
          }
      }
    )

  def load(
    cache: Cache[Task]
  ): Task[JvmIndex] =
    load(cache, defaultIndexUrl)
      .map(jabbaIndexGraalvmJava8Hack)

  def load(): Task[JvmIndex] =
    load(FileCache(), defaultIndexUrl)
      .map(jabbaIndexGraalvmJava8Hack)

  lazy val currentOs: Either[String, String] =
    Option(System.getProperty("os.name")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some(s) if s.contains("windows") => Right("windows")
      case Some(s) if s.contains("linux") => Right("linux")
      case Some(s) if s.contains("mac") => Right("darwin")
      case unrecognized => Left(s"Unrecognized OS: ${unrecognized.getOrElse("")}")
    }

  lazy val currentArchitecture: Either[String, String] =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("x86_64" | "amd64") => Right("amd64")
      case unrecognized => Left(s"Unrecognized CPU architecture: ${unrecognized.getOrElse("")}")
    }

  def defaultOs(): String =
    currentOs match {
      case Right(os) => os
      case Left(err) => throw new Exception(err)
    }

  def defaultArchitecture(): String =
    currentArchitecture match {
      case Right(arch) => arch
      case Left(err) => throw new Exception(err)
    }

  private def parseDescriptor(input: String): Either[String, (ArchiveType, String)] = {
    val idx = input.indexOf('+')
    if (idx < 0)
      Left(s"Malformed url descriptor '$input'")
    else {
      val archiveTypeStr = input.take(idx)
      val url = input.drop(idx + 1)
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
                    name -> versionMap.filterKeys(v => f(name, v)).toMap
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
  ): Either[String, JvmIndexEntry] = {

    def fromVersionConstraint(versionIndex: Map[String, String], version: String) =
      Latest(version) match {
        case Some(_) =>
          // TODO Filter versions depending on latest kind
          Right(versionIndex.maxBy { case (v, _) => Version(v) })
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
            val inInterval = versionIndex.filterKeys(s => c.interval.contains(Version(s)))
            if (inInterval.isEmpty)
              Left(s"No $name version matching '$version' found")
            else {
              val preferredInInterval = inInterval.filterKeys(s => c.preferred.contains(Version(s)))
              val map =
                if (preferredInInterval.isEmpty) inInterval
                else preferredInInterval
              val retained = map.maxBy { case (v, _) => Version(v) }
              Right(retained)
            }
          }

    }

    for {
      os <- os.map(Right(_)).getOrElse(JvmIndex.currentOs)
      arch <- arch.map(Right(_)).getOrElse(JvmIndex.currentArchitecture)
      osIndex <- content.get(os).toRight(s"No JVM found for OS $os")
      archIndex <- osIndex.get(arch).toRight(s"No JVM found for OS $os and CPU architecture $arch")
      versionIndex <- archIndex.get(jdkNamePrefix.getOrElse("") + name).toRight(s"JVM $name not found")
      needs1Prefix = versionIndex.keysIterator.forall(_.startsWith("1."))
      version0 =
        if (needs1Prefix) {
          if (version.startsWith("1.") || version == "1" || version == "1+")
            version
          else
            "1." + version
        } else
          version
      retainedVersionUrlDescriptor <- versionIndex
        .get(version0)
        .map(url => Right((version0, url)))
        .getOrElse(fromVersionConstraint(versionIndex, version0))
      (retainedVersion, urlDescriptor) = retainedVersionUrlDescriptor
      archiveTypeUrl <- parseDescriptor(urlDescriptor)
      (archiveType, url) = archiveTypeUrl
    } yield JvmIndexEntry(os, arch, name, retainedVersion, archiveType, url)
  }

  def available(
    os: Option[String] = None,
    arch: Option[String] = None,
    jdkNamePrefix: Option[String] = Some("jdk@")
  ): Either[String, Map[String, Map[String, String]]] =
    for {
      os <- os.map(Right(_)).getOrElse(JvmIndex.currentOs)
      arch <- arch.map(Right(_)).getOrElse(JvmIndex.currentArchitecture)
      osIndex <- content.get(os).toRight(s"No JVM found for OS $os")
      archIndex <- osIndex.get(arch).toRight(s"No JVM found for OS $os and CPU architecture $arch")
    } yield {
      archIndex.map {
        case (name, versionMap) =>
          val name0 = jdkNamePrefix.fold(name)(name.stripPrefix)
          name0 -> versionMap
      }
    }
}
