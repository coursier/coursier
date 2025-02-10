package coursier.tests

import java.util.concurrent.{ConcurrentHashMap, Executors}

import coursier.core.{Module, ResolutionProcess}
import coursier.util.StringInterpolators._
import coursier.util.Task
import coursier.version.VersionConstraint
import utest._

import scala.jdk.CollectionConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.util.{Properties, Try}

object ResolutionProcessTests extends TestSuite {

  val es = Executors.newFixedThreadPool(4) // ensure threads are daemon?
  val ec = ExecutionContext.fromExecutorService(es)

  override def utestAfterAll(): Unit =
    es.shutdown()

  val tests = Tests {

    test("fetchAll") {

      // check that tasks fetching different versions of the same module are spawned sequentially
      // rather than all at once
      def check(extra: Int): Unit = {

        val mod = mod"org:name"
        val modVers = (1 to (9 + extra))
          .map(_.toString)
          .map(VersionConstraint(_))
          .map((mod, _))

        val called = new ConcurrentHashMap[String, Unit]

        val fetch: ResolutionProcess.Fetch0[Task] = {

          case Seq((`mod`, v)) if v.asString == "9" =>
            val save = Task.delay {
              called.put("9", ())
            }

            save.flatMap(_ => Task.never)

          case Seq(mv @ (`mod`, v)) =>
            val save = Task.delay {
              called.put(v.asString, ())
            }

            save.map(_ => Seq((mv, Left(Seq("w/e")))))

          case _ => sys.error(s"Cannot possibly happen ($modVers)")
        }

        val f = ResolutionProcess.fetchAll(modVers, fetch)
          .future()(ec)

        val delay =
          // seen things fail with 1 second delay on the Windows CI
          if (Properties.isWin) 5.seconds
          else 1.second
        val res = Try(Await.result(f, delay))

        // must have timed out
        assert {
          res.failed.toOption.exists {
            case _: java.util.concurrent.TimeoutException => true
            case _                                        => false
          }
        }

        val called0 = called.asScala.iterator.map(_._1).toSet
        val expectedCalled = (0 to extra)
          .map(9 + _)
          .map(_.toString)
          .toSet
        assert(called0 == expectedCalled)
      }

      test - check(0)
      test - check(1)
      test - check(3)
    }

  }

}
