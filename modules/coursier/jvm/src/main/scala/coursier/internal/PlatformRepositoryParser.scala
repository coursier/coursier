package coursier.internal

import java.net.MalformedURLException

import coursier.LocalRepositories
import coursier.cache.CacheUrl
import coursier.core.{Authentication, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

object PlatformRepositoryParser {

  def repository(input: String): Either[String, Repository] =
    if (input == "ivy2local" || input == "ivy2Local")
      Right(LocalRepositories.ivy2Local)
    else if (input == "ivy2cache" || input == "ivy2Cache")
      Right(LocalRepositories.Dangerous.ivy2Cache)
    else if (input == "m2Local" || input == "m2local")
      Right(LocalRepositories.Dangerous.maven2Local)
    else {
      val repo = SharedRepositoryParser.repository(input)

      val url = repo.right.map {
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

      val validatedUrl = try {
        url.right.map(CacheUrl.url)
      } catch {
        case e: MalformedURLException =>
          Left("Error parsing URL " + url + Option(e.getMessage).fold("")(" (" + _ + ")"))
      }

      validatedUrl.right.flatMap { url =>
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

                repo.right.map {
                  case m: MavenRepository =>
                    m.copy(
                      root = baseUrl,
                      authentication = Some(Authentication(user, password))
                    )
                  case i: IvyRepository =>
                    i.copy(
                      pattern = coursier.ivy.Pattern(
                        coursier.ivy.Pattern.Chunk.Const(baseUrl) +: i.pattern.chunks.dropWhile {
                          case _: coursier.ivy.Pattern.Chunk.Const => true
                          case _ => false
                        }
                      ),
                      authentication = Some(Authentication(user, password))
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
