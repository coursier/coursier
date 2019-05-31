package coursier.parse

import coursier.core.{Classifier, Configuration, Type}
import coursier.{Attributes, Dependency, moduleNameString, moduleString, organizationString}
import utest._

object DependencyParserTests extends TestSuite {

  val tests = Tests {

    val url = "file%3A%2F%2Fsome%2Fencoded%2Furl"

    // Module parsing tests
    "org:name:version" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.defaultCompile)
          assert(dep.attributes == Attributes())
      }
    }

    "org:name:version:config" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4:runtime", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes())
      }
    }

    "org:name:interval:config" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:[1.7,1.8):runtime", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "[1.7,1.8)")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes())
      }
    }

    "single attr" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4:runtime,classifier=tests", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    "single attr with interval" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:[1.7,1.8):runtime,classifier=tests", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "[1.7,1.8)")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    "single attr with url" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4:runtime,url=" + url, "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, extraParams)) =>
          assert(dep.module.organization == org"org.apache.avro")
          assert(dep.module.name == name"avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == Configuration.runtime)
          assert(dep.attributes == Attributes())
          assert(extraParams.isDefinedAt("url"))
          assert(extraParams.getOrElse("url", "") == url)
      }
    }

    "multiple attrs with url" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:1.7.4:runtime,classifier=tests,url=" + url, "2.11.11") match {
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

    "multiple attrs with interval and url" - {
      DependencyParser.dependencyParams("org.apache.avro:avro:[1.7,1.8):runtime,classifier=tests,url=" + url, "2.11.11") match {
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

    "single attr with org::name:version" - {
      DependencyParser.dependencyParams("io.get-coursier.scala-native::sandbox_native0.3:0.3.0-coursier-1,classifier=tests", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"io.get-coursier.scala-native")
          assert(dep.module.name.value.contains("sandbox_native0.3")) // use `contains` to be scala version agnostic
          assert(dep.version == "0.3.0-coursier-1")
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    "single attr with org::name:interval" - {
      DependencyParser.dependencyParams("io.get-coursier.scala-native::sandbox_native0.3:[0.3.0,0.4.0),classifier=tests", "2.11.11") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"io.get-coursier.scala-native")
          assert(dep.module.name.value.contains("sandbox_native0.3")) // use `contains` to be scala version agnostic
          assert(dep.version == "[0.3.0,0.4.0)")
          assert(dep.attributes == Attributes(Type.empty, Classifier.tests))
      }
    }

    "full cross versioned org:::name:version" - {
      DependencyParser.dependencyParams("com.lihaoyi:::ammonite:1.6.7", "2.12.8") match {
        case Left(err) => assert(false)
        case Right((dep, _)) =>
          assert(dep.module.organization == org"com.lihaoyi")
          assert(dep.module.name.value == "ammonite_2.12.8")
          assert(dep.version == "1.6.7")
      }
    }

    "illegal 1" - {
      DependencyParser.dependencyParams("junit:junit:4.12,attr", "2.11.11") match {
        case Left(err) => assert(err.contains("Failed to parse attribute"))
        case Right(dep) => assert(false)
      }
    }

    "illegal 2" - {
      DependencyParser.dependencyParams("a:b:c,batman=robin", "2.11.11") match {
        case Left(err) => assert(err.contains("The only attributes allowed are:"))
        case Right(dep) => assert(false)
      }
    }

    "scala module" - {
      DependencyParser.javaOrScalaDependencyParams("org::name:ver") match {
        case Left(err) => sys.error(err)
        case Right((dep, params)) =>
          assert(params.isEmpty)
          val expected = JavaOrScalaDependency.ScalaDependency(
            Dependency(mod"org:name", "ver", configuration = Configuration.defaultCompile),
            fullCrossVersion = false,
            withPlatformSuffix = false,
            exclude = Set.empty
          )
          assert(dep == expected)
      }
    }

    "full cross versioned scala module" - {
      DependencyParser.javaOrScalaDependencyParams("org:::name:ver") match {
        case Left(err) => sys.error(err)
        case Right((dep, params)) =>
          assert(params.isEmpty)
          val expected = JavaOrScalaDependency.ScalaDependency(
            Dependency(mod"org:name", "ver", configuration = Configuration.defaultCompile),
            fullCrossVersion = true,
            withPlatformSuffix = false,
            exclude = Set.empty
          )
          assert(dep == expected)
      }
    }

    "full cross versioned scala module with config" - {
      DependencyParser.javaOrScalaDependencyParams("org:::name:ver:conf") match {
        case Left(err) => sys.error(err)
        case Right((dep, params)) =>
          assert(params.isEmpty)
          val expected = JavaOrScalaDependency.ScalaDependency(
            Dependency(mod"org:name", "ver", configuration = Configuration("conf")),
            fullCrossVersion = true,
            withPlatformSuffix = false,
            exclude = Set.empty
          )
          assert(dep == expected)
      }
    }
  }

}
