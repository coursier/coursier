package coursier.env

import java.io.File

import dataclass.data

import scala.collection.mutable

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class EnvironmentUpdate(
  set: Seq[(String, String)] = Nil,
  pathLikeAppends: Seq[(String, String)] = Nil
) {

  def +(other: EnvironmentUpdate): EnvironmentUpdate =
    EnvironmentUpdate(
      set ++ other.set,
      pathLikeAppends ++ other.pathLikeAppends
    )

  def isEmpty: Boolean =
    set.isEmpty && pathLikeAppends.isEmpty

  // references previous values with as variables
  def bashScriptUpdates: Seq[(String, String)] =
    updatedEnv(
      k => Some(s"$$$k"),
      ":",
      upfront = true
    )

  // references previous values with as variables
  def batScriptUpdates: Seq[(String, String)] =
    updatedEnv(
      k => Some(s"%$k%"),
      ";",
      upfront = true
    )

  // values are escaped before the references to former values are added, so that the latter
  // still get expanded
  private def escapedUpdates(
    escape: (String, String) => String,
    reference: String => String,
    pathSeparator: String
  ): Seq[(String, String)] =
    EnvironmentUpdate(
      set.map { case (k, v) => k -> escape(k, v) },
      pathLikeAppends.map { case (k, v) => k -> escape(k, v) }
    ).updatedEnv(k => Some(reference(k)), pathSeparator, upfront = true)

  def bashScript: String =
    escapedUpdates(ShellQuoting.posix, k => s"$$$k", ":")
      .map {
        case (k, v) =>
          s"""export $k="$v""""
      }
      .mkString("\n")

  def fishScript: String =
    escapedUpdates(ShellQuoting.fish, k => s"$$$k", ":")
      .map {
        case (k, v) =>
          s"""set -x $k "$v""""
      }
      .mkString("\n")

  def batScript: String =
    escapedUpdates(ShellQuoting.bat, k => s"%$k%", ";")
      .map {
        case (k, v) =>
          // FIXME '%' in values still gets expanded
          s"""set "$k=$v""""
      }
      .mkString("\r\n")

  // puts the "path-like appends" upfront, better not to persist these updates
  def transientUpdates(): Seq[(String, String)] =
    updatedEnv(
      EnvironmentUpdate.defaultGetEnv,
      File.pathSeparator,
      upfront = true
    )

  def updatedEnv(
    getEnv: String => Option[String],
    pathSeparator: String,
    upfront: Boolean
  ): Seq[(String, String)] =
    if (pathLikeAppends.isEmpty)
      set
    else {
      // assuming set and pathLikeAppends don't share keys
      val m = new mutable.HashMap[String, String]
      val l = new mutable.ListBuffer[String]
      for ((k, v) <- pathLikeAppends) {
        if (!m.contains(k))
          l += k
        val formerOpt = m.get(k).orElse(getEnv(k))
        val newValue = formerOpt.fold(v) { p =>
          if (upfront)
            v + pathSeparator + p
          else
            p + pathSeparator + v
        }
        m(k) = newValue
      }
      set ++ l.toList.map(k => k -> m(k))
    }

  def alreadyApplied(): Boolean =
    alreadyApplied(EnvironmentUpdate.defaultGetEnv, File.pathSeparator)

  def alreadyApplied(
    getEnv: String => Option[String],
    pathSeparator: String
  ): Boolean = {

    val sets = set.forall {
      case (k, v) =>
        getEnv(k).contains(v)
    }
    def appends = pathLikeAppends.forall {
      case (k, v) =>
        getEnv(k).exists { p =>
          p.split(pathSeparator) // quote pathSeparator?
            .contains(v)
        }
    }

    sets && appends
  }

}

object EnvironmentUpdate {
  def empty: EnvironmentUpdate =
    EnvironmentUpdate()

  def defaultGetEnv: String => Option[String] =
    k => Option(System.getenv(k))
}
