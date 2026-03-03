package coursierbuild.modules

import com.github.lolgab.mill.mima.Mima
import coursierbuild.Deps.{Deps, ScalaVersions}
import mill.*
import mill.scalajslib.*
import mill.scalajslib.api.*
import mill.scalalib.*

import java.io.File
import java.util.Locale

import scala.util.Properties

trait JsTests extends TestScalaJSModule with CsResourcesTests {
  def jsEnvConfig = Task {
    JsTests.jsEnvConfig(super.jsEnvConfig(), forkEnv())
  }
}

object JsTests {
  def jsEnvConfig(parent: JsEnvConfig, forkEnv: Map[String, String]): JsEnvConfig =
    parent match {
      case node: JsEnvConfig.NodeJs =>
        node.copy(env = node.env ++ forkEnv)
      case other =>
        System.err.println(s"Warning: don't know how to add env vars to JsEnvConfig $other")
        other
    }
}
