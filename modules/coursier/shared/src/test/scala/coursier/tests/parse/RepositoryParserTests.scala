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
    test("bintray-ivy:") {
      val obtained = RepositoryParser.repository("bintray-ivy:scalameta/maven")
      assert(obtained.exists(isIvyRepo))
    }
    test("bintray:") {
      val obtained = RepositoryParser.repository("bintray:scalameta/maven")
      assert(obtained.exists(isMavenRepo))
    }

    test("sbt-plugin:") {
      val res = RepositoryParser.repository("sbt-plugin:releases")
      assert(res.exists(isIvyRepo))
    }

    test("typesafe:ivy-") {
      val res = RepositoryParser.repository("typesafe:ivy-releases")
      assert(res.exists(isIvyRepo))
    }
    test("typesafe:") {
      val res = RepositoryParser.repository("typesafe:releases")
      assert(res.exists(isMavenRepo))
    }

    test("scala-nightlies") {
      val res = RepositoryParser.repository("scala-nightlies")
      assert(res.exists(isMavenRepo))
    }

    test("scala-integration") {
      val res = RepositoryParser.repository("scala-integration")
      assert(res.exists(isMavenRepo))
    }

    test("jitpack") {
      val res = RepositoryParser.repository("jitpack")
      assert(res.exists(isMavenRepo))
    }

    test("clojars") {
      val res = RepositoryParser.repository("clojars")
      assert(res.exists(isMavenRepo))
    }

    test("jcenter") {
      val res = RepositoryParser.repository("jcenter")
      assert(res.exists(isMavenRepo))
    }

    test("google") {
      val res = RepositoryParser.repository("google")
      assert(res.exists(isMavenRepo))
    }

    test("gcs") {
      val res = RepositoryParser.repository("gcs")
      assert(res.exists(isMavenRepo))
    }

    test("gcs-eu") {
      val res = RepositoryParser.repository("gcs-eu")
      assert(res.exists(isMavenRepo))
    }

    test("gcs-asia") {
      val res = RepositoryParser.repository("gcs-asia")
      assert(res.exists(isMavenRepo))
    }

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

    test("apache") {
      test("snapshots") {
        val res = RepositoryParser.repository("apache:snapshots")
        assert(res.exists(isMavenRepo))
      }

      test("releases") {
        val res = RepositoryParser.repository("apache:releases")
        assert(res.exists(isMavenRepo))
      }
    }
  }

}
