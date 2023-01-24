package coursier

import coursier.core.{Activation, Configuration, Extension, Reconciliation}
import coursier.error.ResolutionError
import coursier.ivy.IvyRepository
import coursier.maven.SbtMavenRepository
import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
import coursier.util.ModuleMatchers
import utest._

import scala.async.Async.{async, await}
import scala.collection.compat._

object SbtPluginResolveTests extends TestSuite {
  import TestHelpers.{ec, cache, validateDependencies}

  private val resolve = Resolve()
    .noMirrors
    .withCache(cache)
    .withRepositories(
      Seq(
        SbtMavenRepository(Repositories.central),
        Repositories.sbtPlugin("releases")
      )
    )

  val tests = Tests {
    "config handling" - async {
      // if config handling gets messed up, like the "default" config of some dependencies ends up being pulled
      // where it shouldn't, this surfaces more easily here, as sbt-ci-release depends on other sbt plugins,
      // only available on Ivy repositories and not having a configuration named "default".
      val res = await {
        resolve
          .addDependencies(dep"com.geirsson:sbt-ci-release;scalaVersion=2.12;sbtVersion=1.0:1.2.6")
          .addRepositories(Repositories.sbtPlugin("releases"))
          .future()
      }

      await(validateDependencies(res))
    }
  }
}
