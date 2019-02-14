package coursier

import coursier.cache.internal.Platform
import coursier.cache.{Cache, MockCache}
import coursier.util.Task

import scala.concurrent.{ExecutionContext, Future}

abstract class PlatformTestHelpers {
  val cache: Cache[Task] =
    MockCache("modules/tests/metadata")

  val writeMockData = false

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] =
    Platform.textResource(path)

  def maybeWriteTextResource(path: String, content: String): Unit = {}
}
