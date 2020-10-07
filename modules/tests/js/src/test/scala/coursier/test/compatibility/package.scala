package coursier.test

import coursier.cache.MockCache
import coursier.core.Repository
import coursier.util.Task

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import js.Dynamic.{global => g}

package object compatibility {

  implicit val executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  lazy val fs = g.require("fs")

  private def textResource0(path: String)(implicit ec: ExecutionContext): Future[String] = {
    val p = Promise[String]()

    fs.readFile(path, "utf-8", {
      (err: js.Dynamic, data: js.Dynamic) =>
        if (js.typeOf(err) == "undefined" || err == null) p.success(data.asInstanceOf[String])
        else p.failure(new Exception(err.toString))
        ()
    }: js.Function2[js.Dynamic, js.Dynamic, Unit])

    p.future
  }

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] =
    textResource0("modules/tests/shared/src/test/resources/" + path)

  private val baseRepo = "modules/tests/metadata"

  val taskArtifact: Repository.Fetch[Task] =
    MockCache(baseRepo).fetch

  def tryCreate(path: String, content: String): Unit = {}

}
