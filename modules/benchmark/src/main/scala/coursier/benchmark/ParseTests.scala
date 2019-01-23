package coursier.benchmark

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import coursier.maven.MavenRepository
import coursier.moduleString
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class ParseTests {

  @Benchmark
  def parseSparkParent(state: TestState): Unit = {
    val t = state.repositories.head.find(
      mod"org.apache.spark:spark-parent_2.12",
      "2.4.0",
      state.fetcher
    ).run
    val e = Await.result(t.future()(state.ec), Duration.Inf)
    assert(e.isRight)
  }

  @Benchmark
  def parseSparkParentXmlDom(state: TestState): Unit = {
    val content = state.inMemoryCache.fromCache("https://repo1.maven.org/maven2/org/apache/spark/spark-parent_2.12/2.4.0/spark-parent_2.12-2.4.0.pom")
    val res = MavenRepository.parseRawPomDom(content)
    assert(res.isRight)
  }

  @Benchmark
  def parseSparkParentXmlSaxWip(state: TestState): Unit = {
    val content = state.inMemoryCache.fromCache("https://repo1.maven.org/maven2/org/apache/spark/spark-parent_2.12/2.4.0/spark-parent_2.12-2.4.0.pom")
    val res = Parse.parseRawPomSax(content)
    // assert(res.isRight)
  }

  @Benchmark
  def parseApacheParent(state: TestState): Unit = {
    val t = state.repositories.head.find(
      mod"org.apache:apache",
      "18",
      state.fetcher
    ).run
    val e = Await.result(t.future()(state.ec), Duration.Inf)
    assert(e.isRight)
  }

  @Benchmark
  def parseSparkParentMavenModel(state: TestState): Unit = {
    val b = state
      .inMemoryCache
      .fromCache("https://repo1.maven.org/maven2/org/apache/spark/spark-parent_2.12/2.4.0/spark-parent_2.12-2.4.0.pom")
      .getBytes(StandardCharsets.UTF_8)
    val reader = new MavenXpp3Reader
    val model = reader.read(new ByteArrayInputStream(b))
  }

}
