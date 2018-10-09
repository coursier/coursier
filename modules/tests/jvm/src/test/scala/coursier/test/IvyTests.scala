package coursier.test

import java.io.File

import coursier.core.{Classifier, Configuration, Type}
import coursier.{Attributes, Dependency, Module, moduleNameString, organizationString}
import coursier.ivy.IvyRepository
import utest._

object IvyTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in the platform-dependent XML stubs

  val sbtRepo = IvyRepository.parse(
    "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/" +
      "[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
      "[revision]/[type]s/[artifact](-[classifier]).[ext]",
    dropInfoAttributes = true
  ).right.getOrElse(
    throw new Exception("Cannot happen")
  )

  private val runner = new TestRunner

  val tests = Tests {
    'dropInfoAttributes - {
      runner.resolutionCheck(
        module = Module(
          org"org.scala-js", name"sbt-scalajs", Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
        ),
        version = "0.6.6",
        extraRepos = Seq(sbtRepo),
        configuration = Configuration.defaultCompile
      )
    }

    'versionIntervals - {
      // will likely break if new 0.6.x versions are published :-)

      val mod = Module(
        org"com.github.ddispaltro", name"sbt-reactjs", Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
      )
      val ver = "0.6.+"

      val expectedArtifactUrl = "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/com.github.ddispaltro/sbt-reactjs/scala_2.10/sbt_0.13/0.6.8/jars/sbt-reactjs.jar"

      * - runner.resolutionCheck(
        module = mod,
        version = ver,
        extraRepos = Seq(sbtRepo)
      )

      * - runner.withArtifacts(mod, ver, Attributes(Type.jar), extraRepos = Seq(sbtRepo)) { artifacts =>
        assert(artifacts.exists(_.url == expectedArtifactUrl))
      }
    }

    'testArtifacts - {

      val dep = Dependency(
        Module(org"com.example", name"a_2.11"),
        "0.1.0-SNAPSHOT",
        transitive = false,
        attributes = Attributes()
      )

      val repoBase = new File(HandmadeMetadata.repoBase, "http/ivy.abc.com")
        .toURI
        .toASCIIString
        .stripSuffix("/") + "/"

      val repo = IvyRepository.fromPattern(
        repoBase +: coursier.ivy.Pattern.default,
        dropInfoAttributes = true
      )

      val mainJarUrl = repoBase + "com.example/a_2.11/0.1.0-SNAPSHOT/jars/a_2.11.jar"
      val testJarUrl = repoBase + "com.example/a_2.11/0.1.0-SNAPSHOT/jars/a_2.11-tests.jar"

      "no conf or classifier" - runner.withArtifacts(
        dep = dep.copy(attributes = Attributes(Type.jar)),
        extraRepos = Seq(repo),
        classifierOpt = None
      ) {
        case Seq(artifact) =>
          assert(artifact.url == mainJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }

      "test conf" - {
        "no attributes" - runner.withArtifacts(
          dep = dep.copy(configuration = Configuration.test),
          extraRepos = Seq(repo),
          classifierOpt = None
        ) { artifacts =>
          val urls = artifacts.map(_.url).toSet
          assert(urls(mainJarUrl))
          assert(urls(testJarUrl))
        }

        "attributes" - runner.withArtifacts(
          dep = dep.copy(configuration = Configuration.test, attributes = Attributes(Type.jar)),
          extraRepos = Seq(repo),
          classifierOpt = None
        ) { artifacts =>
          val urls = artifacts.map(_.url).toSet
          assert(urls(mainJarUrl))
          assert(urls(testJarUrl))
        }
      }

      "tests classifier" - {
        val testsDep = dep.copy(attributes = Attributes(Type.jar, Classifier.tests))

        * - runner.withArtifacts(
          deps = Set(dep, testsDep),
          extraRepos = Seq(repo),
          classifierOpt = None
        ) { artifacts =>
          val urls = artifacts.map(_.url).toSet
          assert(urls(mainJarUrl))
          assert(urls(testJarUrl))
        }

        * - runner.withArtifacts(
          dep = testsDep,
          extraRepos = Seq(repo),
          classifierOpt = None
        ) {
          case Seq(artifact) =>
            assert(artifact.url == testJarUrl)
          case other =>
            throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
        }
      }
    }
  }

}
