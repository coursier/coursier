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
  Type
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
    /** Verifies the `android` scenario behaves as the user expects. */
    test("android") {

      def androidCheck(dependencies: Dependency*): Future[Unit] =
        doCheck(
          fetch
            .addRepositories(Repositories.google)
            .withArtifactTypes(Resolution.defaultTypes ++ Seq(Type.Exotic.aar)),
          dependencies
        )

      /** Verifies the `activity` scenario behaves as the user expects. */
      test("activity") {
        androidCheck(dep"androidx.activity:activity:1.8.2")
      }
      /** Verifies the `activity-compose` scenario behaves as the user expects. */
      test("activity-compose") {
        androidCheck(dep"androidx.activity:activity-compose:1.8.2")
      }
      /** Verifies the `runtime` scenario behaves as the user expects. */
      test("runtime") {
        androidCheck(dep"androidx.compose.runtime:runtime:1.3.1")
      }
      /** Verifies the `material3` scenario behaves as the user expects. */
      test("material3") {
        androidCheck(dep"androidx.compose.material3:material3:1.0.1")
      }
    }

    /** Verifies the `spring` scenario behaves as the user expects. */
    test("spring") {
      /** Verifies the `data-rest` scenario behaves as the user expects. */
      test("data-rest") {
        check(dep"org.springframework.boot:spring-boot-starter-data-rest:3.3.4")
      }
      /** Verifies the `graphql` scenario behaves as the user expects. */
      test("graphql") {
        check(dep"org.springframework.boot:spring-boot-starter-graphql:3.3.4")
      }
      /** Verifies the `integration` scenario behaves as the user expects. */
      test("integration") {
        check(dep"org.springframework.boot:spring-boot-starter-integration:3.3.4")
      }
      /** Verifies the `oauth2-client` scenario behaves as the user expects. */
      test("oauth2-client") {
        check(dep"org.springframework.boot:spring-boot-starter-oauth2-client:3.3.4")
      }
      /** Verifies the `web` scenario behaves as the user expects. */
      test("web") {
        check(dep"org.springframework.boot:spring-boot-starter-web:3.3.4")
      }
      /** Verifies the `web-services` scenario behaves as the user expects. */
      test("web-services") {
        check(dep"org.springframework.boot:spring-boot-starter-web-services:3.3.4")
      }
      /** Verifies the `webflux` scenario behaves as the user expects. */
      test("webflux") {
        check(dep"org.springframework.boot:spring-boot-starter-webflux:3.3.4")
      }
      /** Verifies the `security-test` scenario behaves as the user expects. */
      test("security-test") {
        check(dep"org.springframework.security:spring-security-test:6.3.4")
      }
    }

    /** Verifies the `quarkus` scenario behaves as the user expects. */
    test("quarkus") {
      /** Verifies the `rest` scenario behaves as the user expects. */
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
      /** Verifies the `rest-jackson` scenario behaves as the user expects. */
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
      /** Verifies the `hibernate-orm-panache` scenario behaves as the user expects. */
      test("hibernate-orm-panache") {
        check(dep"io.quarkus:quarkus-hibernate-orm-panache:3.15.1")
      }
      /** Verifies the `jdbc-postgresql` scenario behaves as the user expects. */
      test("jdbc-postgresql") {
        check(dep"io.quarkus:quarkus-jdbc-postgresql:3.15.1")
      }
      /** Verifies the `arc` scenario behaves as the user expects. */
      test("arc") {
        check(dep"io.quarkus:quarkus-arc:3.15.1")
      }
      /** Verifies the `hibernate-orm` scenario behaves as the user expects. */
      test("hibernate-orm") {
        check(dep"io.quarkus:quarkus-hibernate-orm:3.15.1")
      }
      /** Verifies the `junit5` scenario behaves as the user expects. */
      test("junit5") {
        check(dep"io.quarkus:quarkus-junit5:3.15.1")
      }
      /** Verifies the `rest-assured` scenario behaves as the user expects. */
      test("rest-assured") {
        check(dep"io.rest-assured:rest-assured:5.5.0")
      }
    }

    /** Verifies the `Module level should exclude correctly` scenario behaves as the user expects. */
    test("Module level should exclude correctly") {
      check(
        dep"junit:junit:4.12"
          .addExclusion(org"org.hamcrest", name"hamcrest-core")
      )
    }

    /** Verifies the `avro exclude xz should not fetch xz` scenario behaves as the user expects. */
    test("avro exclude xz should not fetch xz") {
      check(
        dep"org.apache.avro:avro:1.7.4"
          .addExclusion(org"org.tukaani", name"xz")
      )
    }

    /** Verifies the `avro excluding xz + commons-compress should still fetch xz` scenario behaves as the user expects. */
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

    /** Verifies the `requested xz:1_1 should not have conflicts` scenario behaves as the user expects. */
    test("requested xz:1_1 should not have conflicts") {
      check(
        dep"org.apache.commons:commons-compress:1.4.1",
        dep"org.tukaani:xz:1.1"
      )
    }

    /** Verifies the `should have conflicts` scenario behaves as the user expects. */
    test("should have conflicts") {
      check(
        dep"org.apache.commons:commons-compress:1.5",
        dep"org.tukaani:xz:1.1"
      )
    }

    /** Verifies the `classifier tests should have tests jar` scenario behaves as the user expects. */
    test("classifier tests should have tests jar") {
      check(
        dep"org.apache.commons:commons-compress:1.5,classifier=tests"
      )
    }

    /** Verifies the `mixed vanilla and classifier should have tests jar and main jar` scenario behaves as the user expects. */
    test("mixed vanilla and classifier should have tests jar and main jar") {
      check(
        dep"org.apache.commons:commons-compress:1.5,classifier=tests",
        dep"org.apache.commons:commons-compress:1.5"
      )
    }

    /** Verifies the `bouncy-castle` scenario behaves as the user expects. */
    test("bouncy-castle") {
      check(
        dep"org.apache.pulsar:bouncy-castle-bc:4.0.1"
      )
    }

    /** Verifies the `intransitive` scenario behaves as the user expects. */
    test("intransitive") {
      check(
        dep"org.apache.commons:commons-compress:1.5"
          .withTransitive(false)
      )
    }

    /** Verifies the `external dep url with classifier` scenario behaves as the user expects. */
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

    /** Verifies the `sources` scenario behaves as the user expects. */
    test("sources") {
      doCheck(
        fetch.withClassifiers(Set(Classifier.sources)),
        Seq(dep"org.apache.commons:commons-compress:1.5")
      )
    }

    /** Verifies the `intransitive` scenario behaves as the user expects. */
    test("intransitive") {
      check(
        dep"org.apache.commons:commons-compress:1.5"
          .withTransitive(false)
      )
    }

    /** Verifies the `intransitiveTests` scenario behaves as the user expects. */
    test("intransitiveTests") {
      check(
        dep"org.apache.commons:commons-compress:1.5,classifier=tests"
          .withTransitive(false)
      )
    }

    /** Verifies the `forceVersionTests` scenario behaves as the user expects. */
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

    /** Verifies the `forceVersionIntransitiveTests` scenario behaves as the user expects. */
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

    /** Verifies the `depsWithClassifiers` scenario behaves as the user expects. */
    test("depsWithClassifiers") {
      check(
        dep"com.spotify:helios-testing:0.9.193"
      )
    }

    /** Verifies the `url` scenario behaves as the user expects. */
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

    /** Verifies the `grpc-core` scenario behaves as the user expects. */
    test("grpc-core") {
      check(
        dep"io.grpc:grpc-netty-shaded:1.29.0"
      )
    }

  }

}
