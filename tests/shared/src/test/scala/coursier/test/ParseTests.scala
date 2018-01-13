package coursier.test

import coursier.{MavenRepository, Repository}
import coursier.ivy.IvyRepository
import coursier.util.Parse
import coursier.util.Parse.ModuleParseError
import utest._

object ParseTests extends TestSuite {

  def isMavenRepo(repo: Repository): Boolean =
    repo match {
      case _: MavenRepository => true
      case _ => false
    }

  def isIvyRepo(repo: Repository): Boolean =
    repo match {
      case _: IvyRepository => true
      case _ => false
    }

  val tests = TestSuite {
    "bintray-ivy:" - {
      val obtained = Parse.repository("bintray-ivy:scalameta/maven")
      assert(obtained.exists(isIvyRepo))
    }
    "bintray:" - {
      val obtained = Parse.repository("bintray:scalameta/maven")
      assert(obtained.exists(isMavenRepo))
    }

    "sbt-plugin:" - {
      val res = Parse.repository("sbt-plugin:releases")
      assert(res.exists(isIvyRepo))
    }

    "typesafe:ivy-" - {
      val res = Parse.repository("typesafe:ivy-releases")
      assert(res.exists(isIvyRepo))
    }
    "typesafe:" - {
      val res = Parse.repository("typesafe:releases")
      assert(res.exists(isMavenRepo))
    }

    "jitpack" - {
      val res = Parse.repository("jitpack")
      assert(res.exists(isMavenRepo))
    }

    // Module parsing tests
    "org:name:version" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4") match {
        case Left(err) => assert(false)
        case Right(parsedModule) =>
          assert(parsedModule.module.organization == "org.apache.avro")
          assert(parsedModule.module.name == "avro")
          assert(parsedModule.version == "1.7.4")
          assert(parsedModule.config.isEmpty)
          assert(parsedModule.attrs.isEmpty)
      }
    }

    "org:name:version:conifg" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4:runtime") match {
        case Left(err) => assert(false)
        case Right(parsedModule) =>
          assert(parsedModule.module.organization == "org.apache.avro")
          assert(parsedModule.module.name == "avro")
          assert(parsedModule.version == "1.7.4")
          assert(parsedModule.config == Some("runtime"))
          assert(parsedModule.attrs.isEmpty)
      }
    }

    "single attr" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4:runtime,classifier=tests") match {
        case Left(err) => assert(false)
        case Right(parsedModule) =>
          assert(parsedModule.module.organization == "org.apache.avro")
          assert(parsedModule.module.name == "avro")
          assert(parsedModule.version == "1.7.4")
          assert(parsedModule.config == Some("runtime"))
          assert(parsedModule.attrs == Map("classifier" -> "tests"))
      }
    }

    "multiple attrs" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4:runtime,classifier=tests,nickname=superman") match {
        case Left(err) => assert(false)
        case Right(parsedModule) =>
          assert(parsedModule.module.organization == "org.apache.avro")
          assert(parsedModule.module.name == "avro")
          assert(parsedModule.version == "1.7.4")
          assert(parsedModule.config == Some("runtime"))
          assert(parsedModule.attrs == Map("classifier" -> "tests", "nickname" -> "superman"))
      }
    }

    "single attr with org::name:version" - {
      Parse.moduleVersionConfig("io.get-coursier.scala-native::sandbox_native0.3:0.3.0-coursier-1,attr1=val1") match {
        case Left(err) => assert(false)
        case Right(parsedModule) =>
          assert(parsedModule.module.organization == "io.get-coursier.scala-native")
          assert(parsedModule.module.name.contains("sandbox_native0.3")) // use `contains` to be scala version agnostic
          assert(parsedModule.version == "0.3.0-coursier-1")
          assert(parsedModule.attrs == Map("attr1" -> "val1"))
      }
    }

    "illegal 1" - {
      try {
        Parse.moduleVersionConfig("org.apache.avro:avro,1.7.4:runtime,classifier=tests")
        assert(false) // Parsing should fail but succeeded.
      }
      catch {
        case foo: ModuleParseError => assert(foo.getMessage().contains("':' is not allowed in attribute")) // do nothing
        case _: Throwable =>  assert(false) // Unexpected exception
      }
    }

    "illegal 2" - {
      try {
        Parse.moduleVersionConfig("junit:junit:4.12,attr")
        assert(false) // Parsing should fail but succeeded.
      }
      catch {
        case foo: ModuleParseError => assert(foo.getMessage().contains("Failed to parse attribute")) // do nothing
        case _: Throwable =>  assert(false) // Unexpected exception
      }
    }
  }
}
