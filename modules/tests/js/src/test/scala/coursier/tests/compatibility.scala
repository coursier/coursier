package coursier.tests

import coursier.core.Repository
import coursier.testcache.TestCache
import coursier.util.Task

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import js.Dynamic.{global => g}

object compatibility {

  implicit val executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  lazy val fs      = g.require("fs")
  lazy val process = g.require("process")

  private lazy val testDataDir =
    process.env.asInstanceOf[js.Dictionary[String]].get("COURSIER_TEST_DATA_DIR").getOrElse {
      sys.error("COURSIER_TEST_DATA_DIR env var not set")
    }

  private def textResource0(path: String): Future[String] = {
    val p = Promise[String]()

    fs.readFile(
      path,
      "utf-8",
      {
        (err: js.Dynamic, data: js.Dynamic) =>
          if (js.typeOf(err) == "undefined" || err == null) p.success(data.asInstanceOf[String])
          else p.failure(new Exception(err.toString))
          ()
      }: js.Function2[js.Dynamic, js.Dynamic, Unit]
    )

    p.future
  }

  def textResource(path: String): Future[String] =
    textResource0(testDataDir + "/" + path)

  lazy val taskArtifact: Repository.Fetch[Task] =
    TestCache.cache.fetch

  def tryCreate(path: String, content: String): Unit = {}

}
