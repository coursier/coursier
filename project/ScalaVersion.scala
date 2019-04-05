
object ScalaVersion {

  def scala213 = "2.13.0-RC1"
  def scala212 = "2.12.8"
  def scala211 = "2.11.12"

  val versions = Seq(scala213, scala212, scala211)

  val map = versions
    .map { v =>
      v.split('.').take(2).mkString(".") -> v
    }
    .toMap

}
