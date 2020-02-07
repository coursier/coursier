package coursier.cli.jvm

import cats.data.ValidatedNel
import cats.implicits._

final case class JavaHomeParams(
  shared: SharedJavaParams
)

object JavaHomeParams {
  def apply(options: JavaHomeOptions): ValidatedNel[String, JavaHomeParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    sharedV.map { shared =>
      JavaHomeParams(
        shared
      )
    }
  }
}
