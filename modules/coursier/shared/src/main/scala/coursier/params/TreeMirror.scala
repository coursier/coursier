package coursier.params

import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import dataclass.data

/**
  * Assumes any tree with a prefix in `from` is mirrored under `to`.
  *
  * For example, if `from == Seq("https://a.com/artifacts", "https://artifacts.b.com")`,
  * and `to == "https://mirror.c.com/maven"`
  * it is assumed `"https://a.com/artifacts/a/b/c"` also exists at `"https://mirror.c.com/maven/a/b/c"`,
  * and `"https://artifacts.b.com/foo/e/f/g"` also exists at `"https://mirror.c.com/maven/foo/e/f/g"`.
  */
@data class TreeMirror(
  from: Seq[String],
  to: String
) extends Mirror {

  def matches(repo: Repository): Option[Repository] =
    repo match {
      case m: MavenRepository =>
        val url = m.root
        from
          .find(f => url == f || url.startsWith(f + "/"))
          .map { f =>
            val newRoot = to + url.stripPrefix(f)
            m.withRoot(newRoot).withAuthentication(None)
          }
      case i: IvyRepository =>
        from
          .find(f => i.pattern.startsWith(f) && i.metadataPatternOpt.forall(_.startsWith(f)))
          .map { f =>
            i.withPattern(i.pattern.stripPrefix(f).addPrefix(to))
              .withMetadataPatternOpt(i.metadataPatternOpt.map(_.stripPrefix(f).addPrefix(to)))
              .withAuthentication(None)
          }
      case _ =>
        None
    }
}

object TreeMirror {
  def apply(to: String, first: String, others: String*): TreeMirror =
    new TreeMirror((first +: others).map(_.stripSuffix("/")), to.stripSuffix("/"))
}
