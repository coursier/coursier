package coursier.env

import java.io.File

import dataclass.data

import scala.collection.mutable

@data class EnvironmentUpdate(
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

  def bashScript: String = {
    val q = "\""
    bashScriptUpdates
      .map {
        case (k, v) =>
          // FIXME Escape more?
          s"export $k=$q${v.replace(q, "\\" + q)}$q"
      }
      .mkString("\n")
  }

  def fishScript: String = {
    val q = "\""
    bashScriptUpdates
      .map {
        case (k, v) =>
          s"""set -x $k "${v.replace(q, "\\" + q)}""""
      }
      .mkString("\n")
  }

  def batScript: String = {
    val q = "\""
    batScriptUpdates
      .map {
        case (k, v) =>
          // FIXME Correct way of escaping in bat scripts? Escape more?
          s"set $q$k=${v.replace(q, "\\" + q)}$q"
      }
      .mkString("\r\n")
  }

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
