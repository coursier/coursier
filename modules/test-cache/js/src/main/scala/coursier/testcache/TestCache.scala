package coursier.testcache

import coursier.cache.MockCache

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

object TestCache {

  private lazy val process = g.require("process")

  private lazy val dataDirUri =
    process.env
      .asInstanceOf[js.Dictionary[String]]
      .get("COURSIER_TESTS_METADATA_DIR_URI")
      .getOrElse {
        sys.error("COURSIER_TESTS_METADATA_DIR_URI not set")
      }

  def updateSnapshots = false

  lazy val cache = MockCache(dataDirUri.stripPrefix("file://").stripPrefix("file:"))

}
