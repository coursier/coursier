package coursier
package test

import utest._

import scala.async.Async.{async, await}
import coursier.maven.SbtMavenRepository
import coursier.test.compatibility.executionContext

object SbtPluginResolveTests extends TestSuite {

  protected lazy val runner = new TestRunner(
    repositories = Seq(SbtMavenRepository(Repositories.central), Repositories.sbtPlugin("releases"))
  )

  val tests = Tests {
    test("config handling") {
      async {
        // if config handling gets messed up, like the "default" config of some dependencies ends up being pulled
        // where it shouldn't, this surfaces more easily here, as sbt-ci-release depends on other sbt plugins,
        // only available on Ivy repositories and not having a configuration named "default".
        val dependency = mod"com.geirsson:sbt-ci-release;scalaVersion=2.12;sbtVersion=1.0"
        runner.resolutionCheck(dependency, "1.2.6")
      }
    }
  }
}
