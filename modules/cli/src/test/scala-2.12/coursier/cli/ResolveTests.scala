package coursier.cli

import java.io.{ByteArrayOutputStream, PrintStream}

import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cache.CacheDefaults
import coursier.cli.options.ResolveOptions
import coursier.cli.options.shared.OutputOptions
import coursier.cli.params.ResolveParams
import coursier.cli.resolve.Resolve
import coursier.util.Schedulable
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.junit.JUnitRunner

import scala.concurrent.ExecutionContext

@RunWith(classOf[JUnitRunner])
class ResolveTests extends FlatSpec with BeforeAndAfterAll {

  val pool = Schedulable.fixedThreadPool(CacheDefaults.concurrentDownloadCount)
  val ec = ExecutionContext.fromExecutorService(pool)

  override protected def afterAll(): Unit = {
    pool.shutdown()
  }

  def paramsOrThrow(options: ResolveOptions): ResolveParams =
    ResolveParams(options) match {
      case Validated.Invalid(errors) =>
        sys.error("Got errors:\n" + errors.toList.map(e => s"  $e\n").mkString)
      case Validated.Valid(params0) =>
        params0
    }


  it should "print what depends on" in {
    val options = ResolveOptions(
      whatDependsOn = List("org.htrace:htrace-core")
    )
    val args = RemainingArgs(Seq("org.apache.spark:spark-sql_2.12:2.4.0"), Nil)

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    Resolve.task(params, pool, new PrintStream(stdout, true, "UTF-8"), System.err, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    val expectedOutput =
      """└─ org.htrace:htrace-core:3.0.4
        |   ├─ org.apache.hadoop:hadoop-common:2.6.5
        |   │  └─ org.apache.hadoop:hadoop-client:2.6.5
        |   │     └─ org.apache.spark:spark-core_2.12:2.4.0
        |   │        ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
        |   │        │  └─ org.apache.spark:spark-sql_2.12:2.4.0
        |   │        └─ org.apache.spark:spark-sql_2.12:2.4.0
        |   └─ org.apache.hadoop:hadoop-hdfs:2.6.5
        |      └─ org.apache.hadoop:hadoop-client:2.6.5
        |         └─ org.apache.spark:spark-core_2.12:2.4.0
        |            ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
        |            │  └─ org.apache.spark:spark-sql_2.12:2.4.0
        |            └─ org.apache.spark:spark-sql_2.12:2.4.0
        |""".stripMargin

    assert(output === expectedOutput)
  }

  it should "print results anyway" in {
    val options = ResolveOptions(
      outputOptions = OutputOptions(
        force = true
      )
    )
    val args = RemainingArgs(
      Seq("ioi.get-coursier:coursier-core_2.12:1.1.0-M9", "io.get-coursier:coursier-cache_2.12:1.1.0-M9"),
      Nil
    )

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    val ps = new PrintStream(stdout, true, "UTF-8")
    Resolve.task(params, pool, ps, ps, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
      .replace(sys.props("user.home"), "HOME")
    val expectedOutput =
      """io.get-coursier:coursier-cache_2.12:1.1.0-M9:default
        |io.get-coursier:coursier-core_2.12:1.1.0-M9:default
        |ioi.get-coursier:coursier-core_2.12:1.1.0-M9:default(compile)
        |org.scala-lang:scala-library:2.12.7:default
        |org.scala-lang.modules:scala-xml_2.12:1.1.0:default
        |Error:
        |ioi.get-coursier:coursier-core_2.12:1.1.0-M9
        |  not found: HOME/.ivy2/local/ioi.get-coursier/coursier-core_2.12/1.1.0-M9/ivys/ivy.xml
        |  not found: https://repo1.maven.org/maven2/ioi/get-coursier/coursier-core_2.12/1.1.0-M9/coursier-core_2.12-1.1.0-M9.pom
        |""".stripMargin

    assert(output === expectedOutput)
  }
}
