package coursier.tests.parse

import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepositoryLike
import coursier.parse._
import utest._

object RepositoryParserTests extends TestSuite {

  private def isMavenRepo(repo: Repository): Boolean =
    repo match {
      case _: MavenRepositoryLike => true
      case _                      => false
    }

  private def isIvyRepo(repo: Repository): Boolean =
    repo match {
      case _: IvyRepository => true
      case _                => false
    }

  val tests = Tests {
    /** Verifies the `bintray-ivy:` scenario behaves as the user expects. */
    test("bintray-ivy:") {
      val obtained = RepositoryParser.repository("bintray-ivy:scalameta/maven")
      assert(obtained.exists(isIvyRepo))
    }
    /** Verifies the `bintray:` scenario behaves as the user expects. */
    test("bintray:") {
      val obtained = RepositoryParser.repository("bintray:scalameta/maven")
      assert(obtained.exists(isMavenRepo))
    }

    /** Verifies the `sbt-plugin:` scenario behaves as the user expects. */
    test("sbt-plugin:") {
      val res = RepositoryParser.repository("sbt-plugin:releases")
      assert(res.exists(isIvyRepo))
    }

    /** Verifies the `typesafe:ivy-` scenario behaves as the user expects. */
    test("typesafe:ivy-") {
      val res = RepositoryParser.repository("typesafe:ivy-releases")
      assert(res.exists(isIvyRepo))
    }
    /** Verifies the `typesafe:` scenario behaves as the user expects. */
    test("typesafe:") {
      val res = RepositoryParser.repository("typesafe:releases")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `scala-nightlies` scenario behaves as the user expects. */
    test("scala-nightlies") {
      val res = RepositoryParser.repository("scala-nightlies")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `scala-integration` scenario behaves as the user expects. */
    test("scala-integration") {
      val res = RepositoryParser.repository("scala-integration")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `jitpack` scenario behaves as the user expects. */
    test("jitpack") {
      val res = RepositoryParser.repository("jitpack")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `clojars` scenario behaves as the user expects. */
    test("clojars") {
      val res = RepositoryParser.repository("clojars")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `jcenter` scenario behaves as the user expects. */
    test("jcenter") {
      val res = RepositoryParser.repository("jcenter")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `google` scenario behaves as the user expects. */
    test("google") {
      val res = RepositoryParser.repository("google")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `gcs` scenario behaves as the user expects. */
    test("gcs") {
      val res = RepositoryParser.repository("gcs")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `gcs-eu` scenario behaves as the user expects. */
    test("gcs-eu") {
      val res = RepositoryParser.repository("gcs-eu")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `gcs-asia` scenario behaves as the user expects. */
    test("gcs-asia") {
      val res = RepositoryParser.repository("gcs-asia")
      assert(res.exists(isMavenRepo))
    }

    /** Verifies the `ivy with metadata` scenario behaves as the user expects. */
    test("ivy with metadata") {
      val mainPattern =
        "http://repo/cache/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]"
      val metadataPattern =
        "http://repo/cache/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]-[revision](-[classifier]).[ext]"

      val repo = s"ivy:$mainPattern|$metadataPattern"

      val expected = IvyRepository.parse(mainPattern, Some(metadataPattern)).toOption.get

      val res = RepositoryParser.repository(repo)
      assert(res == Right(expected))
    }

    /** Verifies the `apache` scenario behaves as the user expects. */
    test("apache") {
      /** Verifies the `snapshots` scenario behaves as the user expects. */
      test("snapshots") {
        val res = RepositoryParser.repository("apache:snapshots")
        assert(res.exists(isMavenRepo))
      }

      /** Verifies the `releases` scenario behaves as the user expects. */
      test("releases") {
        val res = RepositoryParser.repository("apache:releases")
        assert(res.exists(isMavenRepo))
      }
    }
  }

}
