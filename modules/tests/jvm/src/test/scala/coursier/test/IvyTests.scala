package coursier.test

import java.io.File

import coursier.core.{Classifier, Configuration, Type}
import coursier.{Attributes, Dependency, Module, ivyRepositoryString, moduleNameString, moduleString, organizationString}
import coursier.ivy.IvyRepository
import coursier.test.compatibility.executionContext
import utest._

object IvyTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in the platform-dependent XML stubs

  val sbtRepo = ivy"https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[defaultPattern]"
    .withDropInfoAttributes(true)

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

    val repoBase = new File(HandmadeMetadata.repoBase, "http/ivy.abc.com")
      .toURI
      .toASCIIString
      .stripSuffix("/") + "/"

    val repo = IvyRepository.fromPattern(
      repoBase +: coursier.ivy.Pattern.default,
      dropInfoAttributes = true
    )

    'changing - {
      "-SNAPSHOT suffix" - {

        val dep = Dependency(mod"com.example:a_2.11", "0.1.0-SNAPSHOT")
          .withTransitive(false)
          .withAttributes(Attributes(Type.jar))

        runner.withArtifacts(
          dep,
          extraRepos = Seq(repo),
          classifierOpt = None
        ) {
          case Seq(artifact) =>
            assert(artifact.changing)
          case other =>
            throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
        }
      }

      "-SNAPSHOT suffix" - {

        val dep = Dependency(mod"com.example:a_2.11", "0.2.0.SNAPSHOT")
          .withTransitive(false)
          .withAttributes(Attributes(Type.jar))

        runner.withArtifacts(
          dep,
          extraRepos = Seq(repo),
          classifierOpt = None
        ) {
          case Seq(artifact) =>
            assert(artifact.changing)
          case other =>
            throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
        }
      }
    }

    'testArtifacts - {

      val dep = Dependency(mod"com.example:a_2.11", "0.1.0-SNAPSHOT")
        .withTransitive(false)
        .withAttributes(Attributes())

      val mainJarUrl = repoBase + "com.example/a_2.11/0.1.0-SNAPSHOT/jars/a_2.11.jar"
      val testJarUrl = repoBase + "com.example/a_2.11/0.1.0-SNAPSHOT/jars/a_2.11-tests.jar"

      "no conf or classifier" - runner.withArtifacts(
        dep = dep.withAttributes(Attributes(Type.jar)),
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
          dep = dep.withConfiguration(Configuration.test),
          extraRepos = Seq(repo),
          classifierOpt = None
        ) { artifacts =>
          val urls = artifacts.map(_.url).toSet
          assert(urls(mainJarUrl))
          assert(urls(testJarUrl))
        }

        "attributes" - runner.withArtifacts(
          dep = dep
            .withConfiguration(Configuration.test)
            .withAttributes(Attributes(Type.jar)),
          extraRepos = Seq(repo),
          classifierOpt = None
        ) { artifacts =>
          val urls = artifacts.map(_.url).toSet
          assert(urls(mainJarUrl))
          assert(urls(testJarUrl))
        }
      }

      "tests classifier" - {
        val testsDep = dep.withAttributes(Attributes(Type.jar, Classifier.tests))

        * - runner.withArtifacts(
          deps = Seq(dep, testsDep),
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
