package coursier.parse

import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import utest._

object RepositoryParserTests extends TestSuite {

  private def isMavenRepo(repo: Repository): Boolean =
    repo match {
      case _: MavenRepository => true
      case _ => false
    }

  private def isIvyRepo(repo: Repository): Boolean =
    repo match {
      case _: IvyRepository => true
      case _ => false
    }

  val tests = Tests {
    "bintray-ivy:" - {
      val obtained = RepositoryParser.repository("bintray-ivy:scalameta/maven")
      assert(obtained.exists(isIvyRepo))
    }
    "bintray:" - {
      val obtained = RepositoryParser.repository("bintray:scalameta/maven")
      assert(obtained.exists(isMavenRepo))
    }

    "sbt-plugin:" - {
      val res = RepositoryParser.repository("sbt-plugin:releases")
      assert(res.exists(isIvyRepo))
    }

    "typesafe:ivy-" - {
      val res = RepositoryParser.repository("typesafe:ivy-releases")
      assert(res.exists(isIvyRepo))
    }
    "typesafe:" - {
      val res = RepositoryParser.repository("typesafe:releases")
      assert(res.exists(isMavenRepo))
    }

    "jitpack" - {
      val res = RepositoryParser.repository("jitpack")
      assert(res.exists(isMavenRepo))
    }

    "clojars" - {
      val res = RepositoryParser.repository("clojars")
      assert(res.exists(isMavenRepo))
    }

    "ivy with metadata" - {
      val mainPattern =
        "http://repo/cache/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]"
      val metadataPattern =
        "http://repo/cache/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[organisation]/[module]/[type]-[revision](-[classifier]).[ext]"

      val repo = s"ivy:$mainPattern|$metadataPattern"

      val expected = IvyRepository.parse(mainPattern, Some(metadataPattern)).toOption.get

      val res = RepositoryParser.repository(repo)
      assert(res == Right(expected))
    }
  }

}
