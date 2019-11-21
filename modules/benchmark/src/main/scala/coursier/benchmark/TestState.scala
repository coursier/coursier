package coursier.benchmark

import coursier.cache.Cache
import coursier.core.{Configuration, ResolutionProcess}
import coursier.{Repositories, Resolve, dependencyString, moduleString}
import coursier.internal.InMemoryCachingFetcher
import coursier.maven.{MavenRepository, Pom}
import coursier.util.Sync
import org.openjdk.jmh.annotations.{Scope, State}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

@State(Scope.Benchmark)
class TestState {

  val initialSparkSqlRes = Resolve.initialResolution(
    Seq(dep"org.apache.spark:spark-sql_2.12:2.4.0")
  )

  val initialCoursierCliRes = Resolve.initialResolution(
    Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M10")
  )

  val repositories = Seq(
    Repositories.central
  )

  val repositoriesDom = {
    val l = Seq(
      MavenRepository("https://repo1.maven.org/maven2")
    )
    for (r <- l)
      r.useSaxParser = false
    l
  }

  val pool = Sync.fixedThreadPool(6)
  val ec = ExecutionContext.fromExecutorService(pool)

  val inMemoryCache = {
    val c = new InMemoryCachingFetcher(Cache.default.fetch)
    val fetch = ResolutionProcess.fetch(repositories, c.fetcher)

    for (initialRes <- Seq(initialSparkSqlRes, initialCoursierCliRes)) {
      val t = Resolve.runProcess(initialRes, fetch)
      Await.result(t.future()(ec), Duration.Inf)
    }

    c.onlyCache()
    c
  }

  val fetcher = inMemoryCache.fetcher

  val fetch = ResolutionProcess.fetch(repositories, fetcher)
  val fetchDom = ResolutionProcess.fetch(repositoriesDom, fetcher)

  val forProjectCache = {

    val modules = Seq(
      mod"org.apache:apache" -> "18",
      mod"org.apache.spark:spark-parent_2.12" -> "2.4.0",
      mod"org.apache.spark:spark-sql_2.12" -> "2.4.0"
    )

    modules.map {
      case (m, v) =>
        val org = m.organization.value
        val name = m.name.value
        val url = s"https://repo1.maven.org/maven2/${org.replace('.', '/')}/$name/$v/$name-$v.pom"
        val str = inMemoryCache.fromCache(url)
        val p = MavenRepository.parseRawPomDom(str).right.get
        val p0 = Pom.addOptionalDependenciesInConfig(
          p.withActualVersionOpt(Some(v))
            .withConfigurations(MavenRepository.defaultConfigurations),
          Set(Configuration.empty, Configuration.default),
          Configuration.optional
        )
        (m, v, p0)
    }
  }

}
