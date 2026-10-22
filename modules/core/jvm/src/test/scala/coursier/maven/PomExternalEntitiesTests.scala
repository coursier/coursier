package coursier.maven

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue

import com.sun.net.httpserver.HttpServer
import utest._

import scala.jdk.CollectionConverters._

import coursier.core.compatibility

object PomExternalEntitiesTests extends TestSuite {

  // Records any request, so that we can check the XML parsers never fetch anything
  private def withServer[T](f: String => T): (T, Seq[String]) = {
    val requests = new ConcurrentLinkedQueue[String]
    val server   = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      exchange => {
        requests.add(exchange.getRequestURI.toString)
        val content = """<!ENTITY leaked "LEAKED">""".getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, content.length)
        exchange.getResponseBody.write(content)
        exchange.close()
      }
    )
    server.start()
    try {
      val res = f(s"http://127.0.0.1:${server.getAddress.getPort}")
      (res, requests.asScala.toVector)
    }
    finally server.stop(0)
  }

  private def pom(doctype: String, description: String): String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |$doctype
       |<project>
       |  <modelVersion>4.0.0</modelVersion>
       |  <groupId>com.example</groupId>
       |  <artifactId>awesome-project</artifactId>
       |  <version>1.0</version>
       |  <description>$description</description>
       |</project>
       |""".stripMargin

  private def check(doctype: String => String, description: String, mustParse: Boolean): Unit = {
    val ((saxRes, domRes), requests) = withServer { url =>
      val content = pom(doctype(url), description)
      val saxRes  =
        try MavenRepository.parseRawPomSax(content).map(_.info.description)
        catch {
          case e: org.xml.sax.SAXException => Left(e.toString)
        }
      val domRes = compatibility.xmlParse(content).map(_.toString)
      (saxRes, domRes)
    }
    assert(requests.isEmpty)
    for (res <- Seq(saxRes, domRes))
      assert(!res.toString.contains("LEAKED"))
    // scala-xml, used by compatibility.xmlParse, rejects DOCTYPEs altogether
    assert(domRes.isLeft)
    if (mustParse)
      assert(saxRes.isRight)
  }

  val tests = Tests {
    test("external DTD") {
      check(
        url => s"""<!DOCTYPE project SYSTEM "$url/project.dtd">""",
        "Foo &nbsp; &copy;",
        mustParse = true
      )
    }
    test("public external DTD") {
      check(
        url => s"""<!DOCTYPE project PUBLIC "-//Foo//DTD Project//EN" "$url/project.dtd">""",
        "Foo",
        mustParse = true
      )
    }
    test("external general entity") {
      check(
        url => s"""<!DOCTYPE project [ <!ENTITY ext SYSTEM "$url/entity"> ]>""",
        "Foo &ext;",
        mustParse = false
      )
    }
    test("external parameter entity") {
      check(
        url => s"""<!DOCTYPE project [ <!ENTITY % ext SYSTEM "$url/param"> %ext; ]>""",
        "Foo &leaked;",
        mustParse = false
      )
    }
    test("local DTD file") {
      val dtd = Files.createTempFile("coursier-pom-test", ".dtd")
      try {
        Files.write(dtd, """<!ENTITY leaked "LEAKED">""".getBytes(StandardCharsets.UTF_8))
        val ((saxRes, domRes), _) = withServer { _ =>
          val content = pom(s"""<!DOCTYPE project SYSTEM "${dtd.toUri}">""", "Foo &leaked;")
          val saxRes  =
            try MavenRepository.parseRawPomSax(content).map(_.info.description)
            catch {
              case e: org.xml.sax.SAXException => Left(e.toString)
            }
          (saxRes, compatibility.xmlParse(content).map(_.toString))
        }
        for (res <- Seq(saxRes, domRes))
          assert(!res.toString.contains("LEAKED"))
      }
      finally Files.deleteIfExists(dtd)
    }
  }
}
