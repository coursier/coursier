package coursier.cli

import coursier.{Fetch, Repositories, Resolve}
import coursier.cli.fetch.JsonReport
import coursier.core.{
  Activation,
  Classifier,
  Configuration,
  Dependency,
  DependencyManagement,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Resolution,
  Type,
  VariantSelector
}
import coursier.parse.{DependencyParser, ModuleParser}
import coursier.testcache.TestCache
import coursier.tests.TestHelpers
import coursier.util.{InMemoryRepository, Task}
import coursier.version.{Version, VersionConstraint}
import utest._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Properties

object JsonReportTests extends TestSuite {

  implicit class StringStuff(val sc: StringContext) extends AnyVal {
    def dep(args: Any*): Dependency = {
      val str = sc.s(args: _*)
      DependencyParser.dependency(
        str,
        scala.util.Properties.versionNumberString,
        Configuration.empty
      ) match {
        case Left(err)   => sys.error(s"Malformed dependency '$str': $err")
        case Right(dep0) => dep0
      }
    }
    def mod(args: Any*): Module = {
      val str = sc.s(args: _*)
      ModuleParser.module(
        str,
        scala.util.Properties.versionNumberString
      ) match {
        case Left(err)   => sys.error(s"Malformed module '$str': $err")
        case Right(mod0) => mod0
      }
    }
    def org(args: Any*): Organization = {
      val str = sc.s(args: _*)
      Organization(str)
    }
    def name(args: Any*): ModuleName = {
      val str = sc.s(args: _*)
      ModuleName(str)
    }
  }

  implicit def ec: ExecutionContext = TestHelpers.cache.ec

  private lazy val dataDirStr = {
    val dir =
      if (Properties.isWin) TestCache.dataDir.toString.replace("\\", "/")
      else TestCache.dataDir.toString
    dir + "/"
  }
  def jsonLines(jsonStr: String): Seq[String] =
    ujson.read(jsonStr)
      .render(indent = 2)
      .linesIterator
      .map(_.replace(dataDirStr, "${CACHE}/"))
      .toVector

  private def resolve = Resolve()
    .noMirrors
    .withCache(TestHelpers.cache)

  private def fetch = Fetch()
    .withResolve(resolve)
    .withCache(TestHelpers.cache)

  def doCheck(
    fetch: Fetch[Task],
    dependencies: Seq[Dependency],
    extraKeyPart: String = ""
  ): Future[Unit] =
    for {
      res <- fetch
        .addDependencies(dependencies: _*)
        .futureResult()
      _ <- TestHelpers.validateDependencies(
        res.resolution,
        fetch.resolutionParams,
        extraKeyPart = extraKeyPart
      )
      _ <- TestHelpers.validateResult(
        s"${TestHelpers.testDataDir}/reports/${TestHelpers.pathFor(res.resolution, fetch.resolutionParams, extraKeyPart = extraKeyPart)}.json"
      ) {
        jsonLines {
          JsonReport.report(
            res.resolution,
            res.fullDetailedArtifacts0,
            useSlashSeparator = Properties.isWin
          )
        }
      }
    } yield ()

  def check(dependencies: Dependency*): Future[Unit] =
    doCheck(fetch, dependencies)

  val tests = Tests {
    test("android") {

      def androidCheck(dependencies: Dependency*): Future[Unit] =
        doCheck(
          fetch
            .addRepositories(Repositories.google)
            .withArtifactTypes(Resolution.defaultTypes ++ Seq(Type.Exotic.aar))
            .addVariantAttributes(
              "org.jetbrains.kotlin.platform.type" -> VariantSelector.VariantMatcher.Equals("jvm")
            ),
          dependencies
        )

      test("activity") {
        androidCheck(dep"androidx.activity:activity:1.8.2")
      }
      test("activity-compose") {
        androidCheck(dep"androidx.activity:activity-compose:1.8.2")
      }
      test("runtime") {
        androidCheck(dep"androidx.compose.runtime:runtime:1.3.1")
      }
      test("material3") {
        androidCheck(dep"androidx.compose.material3:material3:1.0.1")
      }
    }

    test("spring") {
      test("data-rest") {
        check(dep"org.springframework.boot:spring-boot-starter-data-rest:3.3.4")
      }
      test("graphql") {
        check(dep"org.springframework.boot:spring-boot-starter-graphql:3.3.4")
      }
      test("integration") {
        check(dep"org.springframework.boot:spring-boot-starter-integration:3.3.4")
      }
      test("oauth2-client") {
        check(dep"org.springframework.boot:spring-boot-starter-oauth2-client:3.3.4")
      }
      test("web") {
        check(dep"org.springframework.boot:spring-boot-starter-web:3.3.4")
      }
      test("web-services") {
        check(dep"org.springframework.boot:spring-boot-starter-web-services:3.3.4")
      }
      test("webflux") {
        check(dep"org.springframework.boot:spring-boot-starter-webflux:3.3.4")
      }
      test("security-test") {
        check(dep"org.springframework.security:spring-security-test:6.3.4")
      }
    }

    test("quarkus") {
      test("rest") {
        doCheck(
          fetch.withResolutionParams(
            fetch.resolutionParams.withOsInfo(
              Activation.Os(Some("x86_64"), Set("mac", "unix"), Some("mac os x"), Some("10.15.1"))
            )
          ),
          Seq(dep"io.quarkus:quarkus-rest:3.15.1")
        )
      }
      test("rest-jackson") {
        doCheck(
          fetch.withResolutionParams(
            fetch.resolutionParams.withOsInfo(
              Activation.Os(Some("x86_64"), Set("mac", "unix"), Some("mac os x"), Some("10.15.1"))
            )
          ),
          Seq(
            dep"io.quarkus:quarkus-rest-jackson:3.15.1"
          )
        )
      }
      test("hibernate-orm-panache") {
        check(dep"io.quarkus:quarkus-hibernate-orm-panache:3.15.1")
      }
      test("jdbc-postgresql") {
        check(dep"io.quarkus:quarkus-jdbc-postgresql:3.15.1")
      }
      test("arc") {
        check(dep"io.quarkus:quarkus-arc:3.15.1")
      }
      test("hibernate-orm") {
        check(dep"io.quarkus:quarkus-hibernate-orm:3.15.1")
      }
      test("junit5") {
        doCheck(
          fetch.addVariantAttributes(
            "org.gradle.jvm.environment" -> VariantSelector.VariantMatcher.Equals("standard-jvm")
          ),
          Seq(dep"io.quarkus:quarkus-junit5:3.15.1")
        )
      }
      test("rest-assured") {
        check(dep"io.rest-assured:rest-assured:5.5.0")
      }
    }

    test("Module level should exclude correctly") {
      check(
        dep"junit:junit:4.12"
          .addExclusion(org"org.hamcrest", name"hamcrest-core")
      )
    }

    test("avro exclude xz should not fetch xz") {
      check(
        dep"org.apache.avro:avro:1.7.4"
          .addExclusion(org"org.tukaani", name"xz")
      )
    }

    test("avro excluding xz + commons-compress should still fetch xz") {
      check(
        dep"org.apache.avro:avro:1.7.4"
          .addExclusion(org"org.tukaani", name"xz")
          .addOverride(
            org"org.apache.avro",
            name"avro",
            VersionConstraint(""),
            Set(org"org.tukaani" -> name"xz")
          ),
        dep"org.apache.commons:commons-compress:1.4.1"
          .addOverride(
            org"org.apache.avro",
            name"avro",
            VersionConstraint(""),
            Set(org"org.tukaani" -> name"xz")
          )
      )
    }

    test("requested xz:1_1 should not have conflicts") {
      check(
        dep"org.apache.commons:commons-compress:1.4.1",
        dep"org.tukaani:xz:1.1"
      )
    }

    test("should have conflicts") {
      check(
        dep"org.apache.commons:commons-compress:1.5",
        dep"org.tukaani:xz:1.1"
      )
    }

    test("classifier tests should have tests jar") {
      check(
        dep"org.apache.commons:commons-compress:1.5,classifier=tests"
      )
    }

    test("mixed vanilla and classifier should have tests jar and main jar") {
      check(
        dep"org.apache.commons:commons-compress:1.5,classifier=tests",
        dep"org.apache.commons:commons-compress:1.5"
      )
    }

    test("bouncy-castle") {
      check(
        dep"org.apache.pulsar:bouncy-castle-bc:4.0.1"
      )
    }

    test("intransitive") {
      check(
        dep"org.apache.commons:commons-compress:1.5"
          .withTransitive(false)
      )
    }

    test("external dep url with classifier") {
      doCheck(
        fetch.addRepositories(
          InMemoryRepository.forDependencies(
            dep"org.tukaani:xz:1.2" -> "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
          )
        ),
        Seq(
          dep"org.apache.commons:commons-compress:1.5",
          dep"org.tukaani:xz:1.2,classifier=tests"
        ),
        "_customurl"
      )
    }

    test("sources") {
      doCheck(
        fetch.withClassifiers(Set(Classifier.sources)),
        Seq(dep"org.apache.commons:commons-compress:1.5")
      )
    }

    test("intransitive") {
      check(
        dep"org.apache.commons:commons-compress:1.5"
          .withTransitive(false)
      )
    }

    test("intransitiveTests") {
      check(
        dep"org.apache.commons:commons-compress:1.5,classifier=tests"
          .withTransitive(false)
      )
    }

    test("forceVersionTests") {
      doCheck(
        fetch.withResolutionParams(
          fetch.resolutionParams
            .withForceVersion(
              Map(mod"org.apache.commons:commons-compress" -> "1.4.1")
            )
        ),
        Seq(
          dep"org.apache.commons:commons-compress:1.5,classifier=tests"
        )
      )
    }

    test("forceVersionIntransitiveTests") {
      doCheck(
        fetch.withResolutionParams(
          fetch.resolutionParams
            .withForceVersion(
              Map(mod"org.apache.commons:commons-compress" -> "1.4.1")
            )
        ),
        Seq(
          dep"org.apache.commons:commons-compress:1.5,classifier=tests"
            .withTransitive(false)
        )
      )
    }

    test("depsWithClassifiers") {
      check(
        dep"com.spotify:helios-testing:0.9.193"
      )
    }

    test("url") {
      test {
        doCheck(
          fetch.addRepositories(
            InMemoryRepository.forDependencies(
              dep"org.apache.commons:commons-compress:1.5" -> "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
            )
          ),
          Seq(
            dep"org.apache.commons:commons-compress:1.5"
          ),
          "_customurl1"
        )
      }
      test {
        doCheck(
          fetch.addRepositories(
            InMemoryRepository.forDependencies(
              dep"h:i:j" -> "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
            )
          ),
          Seq(
            dep"h:i:j"
          ),
          "_customurl2"
        )
      }
      test {
        doCheck(
          fetch.addRepositories(
            InMemoryRepository.forDependencies(
              dep"org.apache.commons:commons-compress:1.5" -> "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
            )
          ),
          Seq(
            dep"org.apache.commons:commons-compress:1.5,classifier=tests"
          ),
          "_customurl3"
        )
      }
      test {
        doCheck(
          fetch.addRepositories(
            InMemoryRepository.forDependencies(
              dep"org.apache.commons:commons-compress:1.5" -> "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
            )
          ),
          Seq(
            dep"org.apache.commons:commons-compress:1.5",
            dep"org.codehaus.jackson:jackson-mapper-asl:1.8.8"
          ),
          "_customurl4"
        )
      }
    }

    test("grpc-core") {
      check(
        dep"io.grpc:grpc-netty-shaded:1.29.0"
      )
    }

  }

}
