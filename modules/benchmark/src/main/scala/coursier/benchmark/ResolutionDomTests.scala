package coursier.benchmark

import java.util.concurrent.TimeUnit

import coursier.Resolve
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class ResolutionDomTests {

  @Benchmark
  def sparkSql(state: TestState): Unit = {
    val t = Resolve.runProcess(state.initialSparkSqlRes, state.fetchDom)
    Await.result(t.future()(state.ec), Duration.Inf)
  }

  @Benchmark
  def coursierCli(state: TestState): Unit = {
    val t = Resolve.runProcess(state.initialCoursierCliRes, state.fetchDom)
    Await.result(t.future()(state.ec), Duration.Inf)
  }

}
