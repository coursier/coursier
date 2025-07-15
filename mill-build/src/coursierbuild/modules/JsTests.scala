package coursierbuild.modules

import java.io.File
import com.github.lolgab.mill.mima.Mima
import coursierbuild.Deps.{Deps, ScalaVersions}

import mill._, mill.scalalib._, mill.scalajslib._

import java.util.Locale

import scala.util.Properties

trait JsTests extends TestScalaJSModule with CsResourcesTests {
  import mill.scalajslib.api._
  def jsEnvConfig = T {
    super.jsEnvConfig() match {
      case node: JsEnvConfig.NodeJs =>
        node.copy(
          env = node.env ++ forkEnv()
        )
      case other =>
        System.err.println(s"Warning: don't know how to add env vars to JsEnvConfig $other")
        other
    }
  }
}
