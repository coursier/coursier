package coursier.benchmark

import java.io.File
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

import coursier.Fetch
import coursier.launcher.{AssemblyGenerator, Parameters}
import coursier.util.StringInterpolators._
import org.openjdk.jmh.annotations._

/** Writing a fat JAR, as `cs bootstrap --assembly` and `scala-cli package --assembly` do. */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class AssemblyTests {

  @Benchmark
  def assembly(state: AssemblyTests.AssemblyState): Unit =
    AssemblyGenerator.generate(state.parameters, state.output)

  @Benchmark
  def assemblyNoPreamble(state: AssemblyTests.AssemblyState): Unit =
    AssemblyGenerator.generate(state.parameters.copy(preambleOpt = None), state.output)
}

object AssemblyTests {

  @State(Scope.Benchmark)
  class AssemblyState {

    // Many small entries (guava, protobuf, scala3-library, jts) plus service files
    // and duplicated META-INF entries to merge
    val files: Seq[File] = Fetch()
      .addDependencies(
        dep"com.google.guava:guava:33.3.1-jre",
        dep"com.google.protobuf:protobuf-java:3.25.3",
        dep"org.scala-lang:scala3-library_3:3.3.4",
        dep"org.locationtech.jts:jts-core:1.19.0",
        dep"org.apache.commons:commons-lang3:3.14.0"
      )
      .run()

    val parameters: Parameters.Assembly =
      Parameters.Assembly().copy(files = files, mainClass = Some("bench.Main"))

    var output: Path = _

    @Setup
    def setup(): Unit =
      output = Files.createTempFile("coursier-assembly-bench", ".jar")

    @TearDown
    def tearDown(): Unit =
      Files.deleteIfExists(output)
  }
}
