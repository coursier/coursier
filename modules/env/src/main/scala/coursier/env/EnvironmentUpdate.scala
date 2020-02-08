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

  def updatedEnv(): Seq[(String, String)] =
    updatedEnv(
      EnvironmentUpdate.defaultGetEnv,
      File.pathSeparator
    )

  def updatedEnv(
    getEnv: String => Option[String],
    pathSeparator: String
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
        val newValue = formerOpt.fold(v)(p => p + File.pathSeparator + v)
        m(k) = v
      }
      l.toList.map(k => k -> m(k))
    }

}

object EnvironmentUpdate {
  def empty: EnvironmentUpdate =
    EnvironmentUpdate()

  def defaultGetEnv: String => Option[String] =
    k => Option(System.getenv(k))
}
