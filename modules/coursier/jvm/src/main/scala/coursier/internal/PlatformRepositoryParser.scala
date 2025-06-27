package coursier.internal

import java.io.File
import java.net.MalformedURLException

import coursier.LocalRepositories
import coursier.cache.CacheUrl
import coursier.core.{Authentication, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepositoryLike
import coursier.parse.StandardRepository
import coursier.parse.StandardRepository.syntax._

abstract class PlatformRepositoryParser {

  def repository(input: String): Either[String, Repository] =
    repositoryAsStandard(input).map(_.repository)

  def repositoryAsStandard(input: String): Either[String, StandardRepository] =
    repositoryAsStandard(input, maybeFile = false)

  def repository(input: String, maybeFile: Boolean): Either[String, Repository] =
    repositoryAsStandard(input, maybeFile).map(_.repository)

  def repositoryAsStandard(input: String, maybeFile: Boolean): Either[String, StandardRepository] =
    if (input == "ivy2local" || input == "ivy2Local")
      Right(LocalRepositories.ivy2Local.asStandard)
    else if (input == "ivy2cache" || input == "ivy2Cache")
      Right(LocalRepositories.Dangerous.ivy2Cache.asStandard)
    else if (input == "m2Local" || input == "m2local")
      Right(LocalRepositories.Dangerous.maven2Local.asStandard)
    else {
      val repo = SharedRepositoryParser.repositoryAsStandard(input)

      val url = repo.map {
        case StandardRepository.Maven(m) =>
          m.root
        case StandardRepository.Ivy(i) =>
          // FIXME We're not handling metadataPattern here
          i.pattern.chunks.takeWhile {
            case _: coursier.ivy.Pattern.Chunk.Const => true
            case _                                   => false
          }.map(_.string).mkString
      }

      val validatedUrl = url.flatMap { url0 =>
        try Right(CacheUrl.url(url0))
        catch {
          case e: MalformedURLException =>
            val urlErrorMsg =
              "Error parsing URL " + url0 + Option(e.getMessage).fold("")(" (" + _ + ")")

            if (url0.contains(File.separatorChar)) {
              val f = new File(url0)
              if (f.exists() && !f.isDirectory)
                Left(s"$urlErrorMsg, and $url0 not a directory")
              else
                Right(f.toURI.toURL)
            }
            else
              Left(urlErrorMsg)
        }
      }

      validatedUrl.flatMap { url =>
        Option(url.getUserInfo) match {
          case None =>
            repo
          case Some(userInfo) =>
            val authBase = userInfo.split(":", 2) match {
              case Array(user, password) => Authentication(user, password)
              case Array(user)           => Authentication(user)
            }

            val auth = authBase.withHttpsOnly(url.getProtocol != "http")

            val baseUrl = new java.net.URL(
              url.getProtocol,
              url.getHost,
              url.getPort,
              url.getFile
            ).toString

            repo.map {
              case StandardRepository.Maven(m) =>
                val m0 = m.withRoot(baseUrl).withAuthentication(Some(auth))
                StandardRepository.Maven(m0)
              case StandardRepository.Ivy(i) =>
                val i0 = i.withAuthentication(Some(auth)).withPattern(
                  coursier.ivy.Pattern(
                    coursier.ivy.Pattern.Chunk.Const(baseUrl) +: i.pattern.chunks.dropWhile {
                      case _: coursier.ivy.Pattern.Chunk.Const => true
                      case _                                   => false
                    }
                  )
                )
                StandardRepository.Ivy(i0)
            }
        }
      }
    }

}
