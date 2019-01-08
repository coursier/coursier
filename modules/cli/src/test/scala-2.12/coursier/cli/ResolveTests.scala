package coursier.cli

import java.io.{ByteArrayOutputStream, PrintStream}

import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cli.options.ResolveOptions
import coursier.cli.params.ResolveParams
import coursier.cli.resolve.Resolve
import coursier.util.Schedulable
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

import scala.concurrent.ExecutionContext

@RunWith(classOf[JUnitRunner])
class ResolveTests extends FlatSpec {
  it should "print what depends on" in {
    val options = ResolveOptions(
      whatDependsOn = List("org.htrace:htrace-core")
    )
    val args = RemainingArgs(Seq("org.apache.spark:spark-sql_2.12:2.4.0"), Nil)

    val stdout = new ByteArrayOutputStream

    val params = ResolveParams(options) match {
      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Validated.Valid(params0) =>
        params0
    }

    val pool = Schedulable.fixedThreadPool(params.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(pool)

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
