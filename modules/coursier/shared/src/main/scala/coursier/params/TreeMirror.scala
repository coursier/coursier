package coursier.params

import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

/**
  * Assumes any tree with a prefix in `from` is mirrored under `to`.
  *
  * For example, if `from == Seq("https://a.com/artifacts", "https://artifacts.b.com")`,
  * and `to == "https://mirror.c.com/maven"`
  * it is assumed `"https://a.com/artifacts/a/b/c"` also exists at `"https://mirror.c.com/maven/a/b/c"`,
  * and `"https://artifacts.b.com/foo/e/f/g"` also exists at `"https://mirror.c.com/maven/foo/e/f/g"`.
  */
final class TreeMirror private(
  val from: Seq[String],
  val to: String
) extends Mirror {

  override def equals(o: Any): Boolean =
    o match {
      case x: TreeMirror => (this.from == x.from) && (this.to == x.to)
      case _ => false
    }

  override def hashCode: Int =
    37 * (37 * (17 + "coursier.params.TreeMirror".##) + from.##) + to.##

  override def toString: String =
    "TreeMirror(" + from + ", " + to + ")"

  private[this] def copy(from: Seq[String] = from, to: String = to): TreeMirror =
    new TreeMirror(from, to)

  def withFrom(from: Seq[String]): TreeMirror =
    copy(from = from)
  def withTo(to: String): TreeMirror =
    copy(to = to)

  def matches(repo: Repository): Option[Repository] =
    repo match {
      case m: MavenRepository =>
        val url = m.root.stripSuffix("/")
        from
          .find(f => url == f || url.startsWith(f + "/"))
          .map { f =>
            val newRoot = to + url.stripPrefix(f)
            m.copy(root = newRoot, authentication = None)
          }
      case i: IvyRepository =>
        from
          .find(f => i.pattern.startsWith(f) && i.metadataPatternOpt.forall(_.startsWith(f)))
          .map { f =>
            i.copy(
              pattern = i.pattern.stripPrefix(f).addPrefix(to),
              metadataPatternOpt = i.metadataPatternOpt.map(_.stripPrefix(f).addPrefix(to)),
              authentication = None
            )
          }
      case _ =>
        None
    }
}

object TreeMirror {
  def apply(to: String, first: String, others: String*): TreeMirror =
    new TreeMirror((first +: others).map(_.stripSuffix("/")), to.stripSuffix("/"))
}
