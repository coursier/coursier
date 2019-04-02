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
      assert(obtained.right.exists(isIvyRepo))
    }
    "bintray:" - {
      val obtained = RepositoryParser.repository("bintray:scalameta/maven")
      assert(obtained.right.exists(isMavenRepo))
    }

    "sbt-plugin:" - {
      val res = RepositoryParser.repository("sbt-plugin:releases")
      assert(res.right.exists(isIvyRepo))
    }

    "typesafe:ivy-" - {
      val res = RepositoryParser.repository("typesafe:ivy-releases")
      assert(res.right.exists(isIvyRepo))
    }
    "typesafe:" - {
      val res = RepositoryParser.repository("typesafe:releases")
      assert(res.right.exists(isMavenRepo))
    }

    "jitpack" - {
      val res = RepositoryParser.repository("jitpack")
      assert(res.right.exists(isMavenRepo))
    }
  }

}
