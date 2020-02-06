package coursier.cli.jvm

import cats.data.ValidatedNel
import cats.implicits._

final case class JavaParams(
  env: Boolean,
  shared: SharedJavaParams
)

object JavaParams {
  def apply(options: JavaOptions): ValidatedNel[String, JavaParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    sharedV.map { shared =>
      JavaParams(
        options.env,
        shared
      )
    }
  }
}
