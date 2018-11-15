package coursier.cache.protocol

import org.scalatest.{Matchers, WordSpec}

import sys.SystemProperties
import concurrent.duration._

class HttpHandlerTest extends WordSpec with Matchers {

  "HttpHandler" should {
    "set timeouts" in {

      val props = new SystemProperties
      val beforeConn = props.get("COURSIER_CONN_TIMEOUT_SECONDS")
      val beforeRead = props.get("COURSIER_READ_TIMEOUT_SECONDS")

      try {

        val expectedConn = System.currentTimeMillis().toString.take(6)
        val expectedRead = expectedConn.reverse
        props += (
          "COURSIER_CONN_TIMEOUT_SECONDS" -> expectedConn,
          "COURSIER_READ_TIMEOUT_SECONDS" -> expectedRead
        )
        val c = HttpHandler.newClient
        c.getConnectTimeout shouldBe expectedConn.toInt.seconds.toMillis
        c.getReadTimeout shouldBe expectedRead.toInt.seconds.toMillis
      } finally {
        beforeConn.foreach { oldValue =>
          props += ("COURSIER_CONN_TIMEOUT_SECONDS" -> oldValue)
        }
        beforeRead.foreach { oldValue =>
          props += ("COURSIER_READ_TIMEOUT_SECONDS" -> oldValue)
        }
      }
    }
  }
}
