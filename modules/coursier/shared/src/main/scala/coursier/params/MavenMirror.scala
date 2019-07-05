package coursier.params

import coursier.core.Repository
import coursier.maven.MavenRepository

/**
  * Assumes Maven repository roots listed in `from` are mirrored at `to`.
  *
  * If `from` contains `"*"`, it is assumed all Maven repositories in the resolution are mirrored
  * at `to`. Only _Maven_ repositories, not Ivy ones for example. See [[TreeMirror]] to mirror both types
  * of repository.
  */
final class MavenMirror private(
  val from: Seq[String],
  val to: String
) extends Mirror {

  private val matchesAll = from.contains("*")

  override def equals(o: Any): Boolean =
    o match {
      case x: MavenMirror => (this.from == x.from) && (this.to == x.to)
      case _ => false
    }

  override def hashCode: Int =
    37 * (37 * (17 + "coursier.params.MavenMirror".##) + from.##) + to.##

  override def toString: String =
    "MavenMirror(" + from + ", " + to + ")"

  private[this] def copy(from: Seq[String] = from, to: String = to): MavenMirror =
    new MavenMirror(from, to)

  def withFrom(from: Seq[String]): MavenMirror =
    copy(from = from)
  def withTo(to: String): MavenMirror =
    copy(to = to)

  def matches(repo: Repository): Option[Repository] =
    repo match {
      case m: MavenRepository =>
        val url = m.root
        val matches = matchesAll || from.contains(url)
        if (matches)
          Some(m.copy(root = to, authentication = None))
        else
          None
      case _ =>
        None
    }
}

object MavenMirror {
  def apply(to: String, first: String, others: String*): MavenMirror = {
    val from = first +: others
    val from0 =
      if (from.contains("*"))
        Seq("*")
      else
        from.map(_.stripSuffix("/"))

    new MavenMirror(from0, to.stripSuffix("/"))
  }
}
