package coursier.params

import java.net.{URI, URISyntaxException}
import java.util.Locale

import coursier.core.{Authentication, Repository}
import coursier.maven.{MavenRepositoryLike, MavenSettings}

import dataclass.data

/** A mirror declared in a Maven `settings.xml` file
  *
  * `mirrorOf` follows the syntax of the `mirrorOf` element of Maven `settings.xml` files: a
  * comma-separated list of repository identifiers, that can also contain `*` (any repository),
  * `external:*` (any repository that is neither local nor on `file:`), `external:http:*` (any
  * non-local repository accessed over plain HTTP), and negations like `!some-repo`.
  *
  * Maven matches those identifiers against the `id` of the repositories it knows about. coursier
  * repositories have no such identifier, so their root URL is matched instead, along with the
  * well-known Maven identifier of the repository if it has one (`central` for Maven Central).
  *
  * @param to
  *   root of the repository the matched repositories are mirrored at
  * @param authentication
  *   credentials to pass to the mirror, read from the `server` element whose `id` matches the one
  *   of the mirror
  */
@data(setters = false) case class MavenSettingsMirror(
  mirrorOf: String,
  to: String,
  authentication: Option[Authentication] = None
) extends Mirror {

  import MavenSettingsMirror._

  private val to0       = to.stripSuffix("/")
  private val mirrorOf0 = normalize(mirrorOf)
  private val patterns  = split(mirrorOf)

  def matches(repo: Repository): Option[Repository] =
    repo match {
      case m: MavenRepositoryLike if matchesRoot(m.root) =>
        Some(
          m.withRoot(to0)
            .withAuthentication(authentication)
            .withVersionsCheckHasModule(false)
        )
      case _ =>
        None
    }

  private def matchesRoot(root: String): Boolean =
    matchesPattern(
      mirrorOf0,
      patterns,
      identifiers(root),
      {
        case `externalWildcard`     => isExternal(root)
        case `externalHttpWildcard` => isExternalHttp(root)
        case _                      => false
      }
    )
}

object MavenSettingsMirror {

  private val wildcard             = "*"
  private val externalWildcard     = "external:*"
  private val externalHttpWildcard = "external:http:*"
  private val defaultLayout        = "default"

  /** Maven identifiers of the repositories coursier knows the identifier of */
  private val wellKnownIds = Map(
    "https://repo1.maven.org/maven2"       -> "central",
    "http://repo1.maven.org/maven2"        -> "central",
    "https://repo.maven.apache.org/maven2" -> "central",
    "http://repo.maven.apache.org/maven2"  -> "central"
  )

  /** Trims a pattern, and drops the trailing `/` of the repository root URLs used as identifiers */
  private def normalize(pattern: String): String = {
    val trimmed = pattern.trim
    if (trimmed.length > 1 && trimmed.startsWith("!"))
      "!" + trimmed.substring(1).stripSuffix("/")
    else
      trimmed.stripSuffix("/")
  }

  private def split(input: String): Seq[String] =
    input.split(',').iterator.map(normalize).filter(_.nonEmpty).toVector

  private def identifiers(root: String): Set[String] = {
    val root0 = root.stripSuffix("/")
    wellKnownIds.get(root0).fold(Set(root0))(id => Set(root0, id))
  }

  /** Same logic as `org.apache.maven.bridge.MavenRepositorySystem.matchPattern`
    *
    * A negation wins over the rest and stops the iteration, and so does an exact match, while
    * wildcards don't, so that a negation coming after them is taken into account.
    */
  private def matchesPattern(
    input: String,
    patterns: Seq[String],
    ids: Set[String],
    matchesWildcard: String => Boolean
  ): Boolean =
    if (input == wildcard || ids(input)) true
    else {
      var result    = false
      var stopped   = false
      val patterns0 = patterns.iterator
      while (!stopped && patterns0.hasNext) {
        val pattern = patterns0.next()
        if (pattern.length > 1 && pattern.startsWith("!")) {
          if (ids(pattern.substring(1))) {
            result = false
            stopped = true
          }
        }
        else if (ids(pattern)) {
          result = true
          stopped = true
        }
        else if (pattern == wildcard || matchesWildcard(pattern))
          result = true
      }
      result
    }

  private def schemeAndHost(url: String): Option[(String, String)] =
    try {
      val uri = new URI(url)
      Option(uri.getScheme).map { scheme =>
        (scheme.toLowerCase(Locale.ROOT), Option(uri.getHost).getOrElse(""))
      }
    }
    catch {
      case _: URISyntaxException =>
        None
    }

  private def isLocal(host: String): Boolean =
    host == "localhost" || host == "127.0.0.1"

  private def isExternal(url: String): Boolean =
    schemeAndHost(url).exists {
      case (scheme, host) =>
        scheme != "file" && !isLocal(host)
    }

  private def isExternalHttp(url: String): Boolean =
    schemeAndHost(url).exists {
      case (scheme, host) =>
        (scheme == "http" || scheme == "dav" || scheme == "dav+http") && !isLocal(host)
    }

  /** Whether `mirrorOfLayouts` applies to repositories using the `default` Maven layout
    *
    * coursier only supports that layout, so mirrors that don't apply to it are left out.
    */
  private def matchesDefaultLayout(mirrorOfLayouts: String): Boolean = {
    val input = normalize(mirrorOfLayouts)
    input.isEmpty || matchesPattern(input, split(input), Set(defaultLayout), _ => false)
  }

  /** The mirrors of `settings` coursier can honor
    *
    * Mirrors that block the repositories they match, and mirrors that don't apply to the `default`
    * Maven layout, are left out: coursier has no equivalent for the former, and doesn't support the
    * other layouts. Credentials are read from the `server` element whose `id` matches the one of
    * the mirror.
    */
  def fromSettings(settings: MavenSettings): Seq[MavenSettingsMirror] =
    settings
      .mirrors
      .filter(mirror => !mirror.blocked && matchesDefaultLayout(mirror.mirrorOfLayouts))
      .map { mirror =>
        val authentication =
          for {
            server   <- settings.server(mirror.id)
            user     <- server.username
            password <- server.password
          } yield Authentication(user, password)
        MavenSettingsMirror(mirror.mirrorOf, mirror.url, authentication)
      }
}
