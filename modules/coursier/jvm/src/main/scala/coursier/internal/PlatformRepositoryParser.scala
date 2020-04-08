package coursier.internal

import java.io.File
import java.net.MalformedURLException

import coursier.LocalRepositories
import coursier.cache.CacheUrl
import coursier.core.{Authentication, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

abstract class PlatformRepositoryParser {

  def repository(input: String): Either[String, Repository] =
    repository(input, maybeFile = false)

  def repository(input: String, maybeFile: Boolean): Either[String, Repository] =
    if (input == "ivy2local" || input == "ivy2Local")
      Right(LocalRepositories.ivy2Local)
    else if (input == "ivy2cache" || input == "ivy2Cache")
      Right(LocalRepositories.Dangerous.ivy2Cache)
    else if (input == "m2Local" || input == "m2local")
      Right(LocalRepositories.Dangerous.maven2Local)
    else {
      val repo = SharedRepositoryParser.repository(input)

      val url = repo.map {
        case m: MavenRepository =>
          m.root
        case i: IvyRepository =>
          // FIXME We're not handling metadataPattern here
          i.pattern.chunks.takeWhile {
            case _: coursier.ivy.Pattern.Chunk.Const => true
            case _ => false
          }.map(_.string).mkString
        case r =>
          sys.error(s"Unrecognized repository: $r")
      }

      val validatedUrl = url.flatMap { url0 =>
        try Right(CacheUrl.url(url0))
        catch {
          case e: MalformedURLException =>

            val urlErrorMsg = "Error parsing URL " + url0 + Option(e.getMessage).fold("")(" (" + _ + ")")

            if (url0.contains(File.separatorChar)) {
              val f = new File(url0)
              if (f.exists() && !f.isDirectory)
                Left(s"$urlErrorMsg, and $url0 not a directory")
              else
                Right(f.toURI.toURL)
            } else
              Left(urlErrorMsg)
        }
      }

      validatedUrl.flatMap { url =>
        Option(url.getUserInfo) match {
          case None =>
            repo
          case Some(userInfo) =>
            userInfo.split(":", 2) match {
              case Array(user, password) =>
                val baseUrl = new java.net.URL(
                  url.getProtocol,
                  url.getHost,
                  url.getPort,
                  url.getFile
                ).toString

                val auth = Authentication(user, password)
                  .withHttpsOnly(url.getProtocol != "http")

                repo.map {
                  case m: MavenRepository =>
                    m.withRoot(baseUrl).withAuthentication(Some(auth))
                  case i: IvyRepository =>
                    i.withAuthentication(Some(auth)).withPattern(
                      coursier.ivy.Pattern(
                        coursier.ivy.Pattern.Chunk.Const(baseUrl) +: i.pattern.chunks.dropWhile {
                          case _: coursier.ivy.Pattern.Chunk.Const => true
                          case _ => false
                        }
                      )
                    )
                  case r =>
                    sys.error(s"Unrecognized repository: $r")
                }

              case _ =>
                Left(s"No password found in user info of URL $url")
            }
        }
      }
    }

}
