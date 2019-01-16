package coursier.cli

import java.io.{ByteArrayOutputStream, PrintStream}

import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cache.CacheDefaults
import coursier.cli.options.ResolveOptions
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
}
