package coursier.tests.parse

import coursier.parse._
import coursier.core.{
  Attributes,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Publication,
  Type
}
import coursier.util.StringInterpolators._
import utest._

object DependencyParserTests extends TestSuite {

  val tests = Tests {

    val url = "file%3A%2F%2Fsome%2Fencoded%2Furl"

    // Module parsing tests
    test("org:name:version") {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.empty)
          assert(dep.attributes == Attributes.empty)
      }
    }

    test("org:name:version:config") {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4:runtime", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes.empty)
      }
    }

    test("org:name:interval:config") {
      DependencyParser.dependencyParams("org.apache.avro:avro:[1.7,1.8):runtime", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "[1.7,1.8)")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes.empty)
      }
    }

    test("single attr") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:1.7.4:runtime,classifier=tests",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    test("extension") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:1.7.4:runtime,ext=exe",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.publication == Publication("", Type.empty, Extension("exe"), Classifier.empty))
      }
    }

    test("type") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:1.7.4:runtime,type=typetype",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          val expectedPublication = Publication(
            "",
            Type("typetype"),
            Extension.empty,
            Classifier.empty
          )
          assert(dep.publication == expectedPublication)
      }
    }

    test("extension and type") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:1.7.4:runtime,ext=exe,type=typetype",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          val expectedPublication = Publication(
            "",
            Type("typetype"),
            Extension("exe"),
            Classifier.empty
          )
          assert(dep.publication == expectedPublication)
      }
    }

    test("single attr with interval") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:[1.7,1.8):runtime,classifier=tests",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "[1.7,1.8)")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    test("single attr with url") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:1.7.4:runtime,url=" + url,
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, extraParams)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes.empty)
          assert(extraParams.isDefinedAt("url"))
          assert(extraParams.getOrElse("url", "") == url)
      }
    }

    test("multiple attrs with url") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:1.7.4:runtime,classifier=tests,url=" + url,
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, extraParams)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
          assert(extraParams.isDefinedAt("url"))
          assert(extraParams.getOrElse("url", "") == url)
      }
    }

    test("multiple attrs with interval and url") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:[1.7,1.8):runtime,classifier=tests,url=" + url,
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, extraParams)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "[1.7,1.8)")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
          assert(extraParams.isDefinedAt("url"))
          assert(extraParams.getOrElse("url", "") == url)
      }
    }

    test("multiple attrs with interval url and exclusions") {
      DependencyParser.dependencyParams(
        "org.apache.avro:avro:[1.7,1.8):runtime,classifier=tests,url=" + url + ",exclude=org%nme",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, extraParams)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "[1.7,1.8)")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
          assert(extraParams.isDefinedAt("url"))
          assert(extraParams.getOrElse("url", "") == url)
          assert(dep.minimizedExclusions.toSet() == Set((org"org", name"nme")))
      }
    }

    test("single attr with org::name:version") {
      DependencyParser.dependencyParams(
        "io.get-coursier.scala-native::sandbox_native0.3:0.3.0-coursier-1,classifier=tests",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"io.get-coursier.scala-native")
          // use `contains` to be scala version agnostic
          assert(dep.module.name.value.contains("sandbox_native0.3_"))
          assert(dep.version == "0.3.0-coursier-1")
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    test("single attr with org::name:interval") {
      DependencyParser.dependencyParams(
        "io.get-coursier.scala-native::sandbox_native0.3:[0.3.0,0.4.0),classifier=tests",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"io.get-coursier.scala-native")
          // use `contains` to be scala version agnostic
          assert(dep.module.name.value.contains("sandbox_native0.3"))
          assert(dep.version == "[0.3.0,0.4.0)")
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    test("multiple attr with org::name:interval and exclusion") {
      DependencyParser.dependencyParams(
        "io.get-coursier.scala-native::sandbox_native0.3:[0.3.0,0.4.0),classifier=tests,exclude=foo%bar",
        "2.11.11"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"io.get-coursier.scala-native")
          // use `contains` to be scala version agnostic
          assert(dep.module.name.value.contains("sandbox_native0.3"))
          assert(dep.version == "[0.3.0,0.4.0)")
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
          assert(dep.minimizedExclusions.toSet() == Set((org"foo", name"bar")))
      }
    }

    test("full cross versioned org:::name:version") {
      DependencyParser.dependencyParams("com.lihaoyi:::ammonite:1.6.7", "2.12.8") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"com.lihaoyi")
          assert(dep.module.name.value == "ammonite_2.12.8")
          assert(dep.version == "1.6.7")
      }
    }

    test("full cross versioned org:::name:version with exclusion") {
      DependencyParser.dependencyParams(
        "com.lihaoyi:::ammonite:1.6.7,exclude=aa%*",
        "2.12.8"
      ) match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"com.lihaoyi")
          assert(dep.module.name.value == "ammonite_2.12.8")
          assert(dep.version == "1.6.7")
          assert(dep.minimizedExclusions.toSet() == Set((org"aa", name"*")))
      }
    }

    test("illegal 1") {
      DependencyParser.dependencyParams("junit:junit:4.12,classifier", "2.11.11") match {
        case Left(err)  => assert(err.contains("Invalid empty classifier attribute"))
        case Right(dep) => assert(false)
      }
    }

    test("illegal 2") {
      DependencyParser.dependencyParams("a:b:c,batman=robin", "2.11.11") match {
        case Left(err)  => assert(err.contains("The only attributes allowed are:"))
        case Right(dep) => assert(false)
      }
    }

    test("illegal 3 malformed exclude") {
      DependencyParser.dependencyParams("a:b:c,exclude=aaa", "2.11.11") match {
        case Left(err)  => assert(err.contains("Unrecognized excluded module"))
        case Right(dep) => assert(false)
      }
    }

    test("scala module") {
      DependencyParser.javaOrScalaDependencyParams("org::name:ver") match {
        case Left(err) => sys.error(err)
        case Right((dep, params)) =>
          assert(params.isEmpty)
          val expected = JavaOrScalaDependency.ScalaDependency(
            Dependency(mod"org:name", "ver").withConfiguration(Configuration.empty),
            fullCrossVersion = false,
            withPlatformSuffix = false,
            exclude = Set.empty
          )
          assert(dep == expected)
      }
    }

    test("full cross versioned scala module") {
      DependencyParser.javaOrScalaDependencyParams("org:::name:ver") match {
        case Left(err) => sys.error(err)
        case Right((dep, params)) =>
          assert(params.isEmpty)
          val expected = JavaOrScalaDependency.ScalaDependency(
            Dependency(mod"org:name", "ver").withConfiguration(Configuration.empty),
            fullCrossVersion = true,
            withPlatformSuffix = false,
            exclude = Set.empty
          )
          assert(dep == expected)
      }
    }

    test("full cross versioned scala module with config") {
      DependencyParser.javaOrScalaDependencyParams("org:::name:ver:conf") match {
        case Left(err) => sys.error(err)
        case Right((dep, params)) =>
          assert(params.isEmpty)
          val expected = JavaOrScalaDependency.ScalaDependency(
            Dependency(mod"org:name", "ver").withConfiguration(Configuration("conf")),
            fullCrossVersion = true,
            withPlatformSuffix = false,
            exclude = Set.empty
          )
          assert(dep == expected)
      }
    }

    test("'/' and '\\' are invalid in organization") {
      DependencyParser.dependencyParams("org/apache/avro:avro:1.7.4", "2.11.11") match {
        case Left(err)       => assert(err.contains("org/apache/avro"))
        case Right((dep, _)) => assert(false)
      }
    }

    test("'/' and '\\' are invalid in module name") {
      DependencyParser.dependencyParams("org-apache-avro:avro\\avro:1.7.4", "2.11.11") match {
        case Left(err)       => assert(err.contains("avro\\avro"))
        case Right((dep, _)) => assert(false)
      }
    }

    test("'/' and '\\' are invalid in version") {
      DependencyParser.dependencyParams("org-apache-avro:avro:1.7.4/SNAPSHOT", "2.11.11") match {
        case Left(err)       => assert(err.contains("1.7.4/SNAPSHOT"))
        case Right((dep, _)) => assert(false)
      }
    }
  }

}
