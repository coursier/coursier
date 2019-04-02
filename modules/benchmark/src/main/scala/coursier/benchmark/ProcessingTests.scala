package coursier.benchmark

import java.util.concurrent.TimeUnit

import coursier.Repositories
import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class ProcessingTests {

  @Benchmark
  def sparkSql(state: TestState): Unit = {

    var res = state.initialSparkSqlRes
    for ((m, v, p) <- state.forProjectCache)
      res = res.addToProjectCache((m, v) -> (Repositories.central, p))

  }

}
