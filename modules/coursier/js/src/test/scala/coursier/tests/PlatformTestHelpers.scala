package coursier.tests

import coursier.cache.internal.Platform
import coursier.cache.{Cache, MockCache}
import coursier.util.Task

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

object PlatformTestHelpers {
  lazy val process = g.require("process")
}

abstract class PlatformTestHelpers {

  lazy val testDataDir =
    PlatformTestHelpers.process.env
      .asInstanceOf[js.Dictionary[String]]
      .get("COURSIER_TEST_DATA_DIR")
      .getOrElse {
        sys.error("COURSIER_TEST_DATA_DIR not set")
      }

  val cache: Cache[Task] =
    MockCache("modules/tests/metadata")

  val handmadeMetadataBase = "file:modules/tests/handmade-metadata/data/"

  val handmadeMetadataCache: Cache[Task] =
    MockCache(handmadeMetadataBase.stripPrefix("file:"))

  val updateSnapshots = false

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] =
    Platform.textResource(path)

  def maybeWriteTextResource(path: String, content: String): Unit = {}

  private lazy val sha1Module = g.require("sha1")

  def sha1(s: String): String =
    sha1Module(s).asInstanceOf[String].dropWhile(_ == '0')

  def maybePrintConsistencyDiff(fromOrdered: Seq[String], fromMinimized: Seq[String]): Unit = ()
}
