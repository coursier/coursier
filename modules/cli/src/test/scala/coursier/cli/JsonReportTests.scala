package coursier.cli

import coursier.{Fetch, Repositories, Resolve}
import coursier.cli.fetch.JsonOutput
import coursier.core.{Activation, Dependency, Resolution, Type}
import coursier.params.ResolutionParams
import coursier.testcache.TestCache
import coursier.tests.TestHelpers
import coursier.util.StringInterpolators._
import coursier.util.Task
import utest._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

object JsonReportTests extends TestSuite {

  implicit def ec: ExecutionContext = TestHelpers.cache.ec

  def jsonLines(jsonStr: String): Seq[String] =
    ujson.read(jsonStr)
      .render(indent = 2)
      .linesIterator
      .map(_.replace(TestCache.dataDir.toString + "/", "${CACHE}/"))
      .toVector

  private val resolve = Resolve()
    .noMirrors
    .withCache(TestHelpers.cache)
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          Activation.Os(
            Some("x86_64"),
            Set("mac", "unix"),
            Some("mac os x"),
            Some("10.15.1")
          )
        }
        .withJdkVersion("1.8.0_121")
    )

  private val fetch = Fetch()
    .withResolve(resolve)
    .withCache(TestHelpers.cache)
    .withArtifactTypes(Resolution.defaultTypes ++ Seq(Type.Exotic.aar))

  def doCheck(fetch: Fetch[Task], dependencies: Seq[Dependency]): Future[Unit] =
    async {
      val res = await {
        fetch
          .addDependencies(dependencies: _*)
          .futureResult()
      }
      await(TestHelpers.validateDependencies(res.resolution))

      TestHelpers.validateResult(
        s"${TestHelpers.testDataDir}/reports/${TestHelpers.pathFor(res.resolution, fetch.resolutionParams)}.json"
      ) {
        jsonLines {
          JsonOutput.report(
            res.resolution,
            res.detailedArtifacts.map {
              case (dep, pub, art, _) =>
                (dep, pub, art)
            },
            res.artifacts,
            Set.empty,
            printExclusions = false
          )
        }
      }
    }

  def check(dependencies: Dependency*): Future[Unit] =
    doCheck(fetch, dependencies)

  val tests = Tests {
    test("android") {

      def androidCheck(dependencies: Dependency*): Future[Unit] =
        async {
          val res = await {
            fetch
              .addRepositories(Repositories.google)
              .addDependencies(dependencies: _*)
              .futureResult()
          }
          await(TestHelpers.validateDependencies(res.resolution))

          TestHelpers.validateResult(
            s"${TestHelpers.testDataDir}/reports/${TestHelpers.pathFor(res.resolution, fetch.resolutionParams)}.json"
          ) {
            jsonLines {
              JsonOutput.report(
                res.resolution,
                res.detailedArtifacts.map {
                  case (dep, pub, art, _) =>
                    (dep, pub, art)
                },
                res.artifacts,
                Set.empty,
                printExclusions = false
              )
            }
          }
        }

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
        check(dep"io.quarkus:quarkus-rest:3.15.1")
      }
      test("rest-jackson") {
        check(dep"io.quarkus:quarkus-rest-jackson:3.15.1")
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
        check(dep"io.quarkus:quarkus-junit5:3.15.1")
      }
      test("rest-assured") {
        check(dep"io.rest-assured:rest-assured:5.5.0")
      }
    }
  }

}
