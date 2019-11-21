package coursier.params

import coursier.core.Repository
import coursier.maven.MavenRepository
import dataclass.data

/**
  * Assumes Maven repository roots listed in `from` are mirrored at `to`.
  *
  * If `from` contains `"*"`, it is assumed all Maven repositories in the resolution are mirrored
  * at `to`. Only _Maven_ repositories, not Ivy ones for example. See [[TreeMirror]] to mirror both types
  * of repository.
  */
@data class MavenMirror(
  from: Seq[String],
  to: String
) extends Mirror {

  private val matchesAll = from.contains("*")

  def matches(repo: Repository): Option[Repository] =
    repo match {
      case m: MavenRepository =>
        val url = m.root
        val matches = matchesAll || from.contains(url)
        if (matches)
          Some(m.withRoot(to).withAuthentication(None).withVersionsCheckHasModule(false))
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

    MavenMirror(from0, to.stripSuffix("/"))
  }
}
