package coursier.tests

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}

import coursier.cache.{CacheUrl, FileCache}
import coursier.cli.fetch.Fetch
import coursier.core.{Dependency, Module, Organization}
import coursier.maven.MavenRepository
import coursier.util.{Sync, Task}
import utest._

import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration.Duration

object CustomHandlerFactoryTests extends TestSuite {

  // Create a custom protocol handler for testing
  class CustomTestHandler extends URLStreamHandlerFactory {
    def createURLStreamHandler(protocol: String): URLStreamHandler = 
      if (protocol == "customtest") {
        new URLStreamHandler {
          protected def openConnection(url: URL): URLConnection = {
            new URLConnection(url) {
              def connect(): Unit = ()
              
              override def getInputStream(): InputStream = {
                val content = s"""<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>com.example</groupId>
  <artifactId>test-artifact</artifactId>
  <versioning>
    <latest>1.0.0</latest>
    <release>1.0.0</release>
    <versions>
      <version>1.0.0</version>
    </versions>
    <lastUpdated>20231201000000</lastUpdated>
  </versioning>
</metadata>"""
                new ByteArrayInputStream(content.getBytes("UTF-8"))
              }
              
              override def getContentLength(): Int = getInputStream().available()
            }
          }
        }
      } else null
  }

  val tests = Tests {
    
    /** Verifies the `CacheUrl.url with custom handler factory` scenario behaves as the user expects. */
    test("CacheUrl.url with custom handler factory") {
      val customHandler = new CustomTestHandler()
      
      // Test that custom handler factory is used
      val url = CacheUrl.url("customtest://example.com/test", Some(customHandler))
      assert(url.getProtocol == "customtest")
      assert(url.getHost == "example.com")
      assert(url.getPath == "/test")
      
      // Test that connection can be opened
      val conn = url.openConnection()
      assert(conn != null)
      conn.connect()
      assert(conn.getInputStream() != null)
    }
    
    /** Verifies the `CacheUrl.url falls back to classloader resolution` scenario behaves as the user expects. */
    test("CacheUrl.url falls back to classloader resolution") {
      val customHandler = new CustomTestHandler()
      
      // Test with a protocol not handled by custom factory - should fall back
      val url = CacheUrl.url("testprotocol://example.com/test", Some(customHandler))
      assert(url.getProtocol == "testprotocol")
    }
    
    /** Verifies the `FileCache with custom handler factory` scenario behaves as the user expects. */
    test("FileCache with custom handler factory") {
      val customHandler = new CustomTestHandler()
      val tmpDir = java.nio.file.Files.createTempDirectory("coursier-test")
      
      val cache = FileCache[Task]()
        .withLocation(tmpDir.toFile)
        .withCustomHandlerFactory(Some(customHandler))
      
      // Test that cache was created successfully with custom handler factory
      assert(cache.customHandlerFactory.isDefined)
      assert(cache.customHandlerFactory.get == customHandler)
      
      // Clean up
      java.nio.file.Files.deleteIfExists(tmpDir)
    }
    
    /** Verifies the `Fetch task with custom handler factory` scenario behaves as the user expects. */
    test("Fetch task with custom handler factory") {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val pool = Sync.fixedThreadPool(1)
      val customHandler = new CustomTestHandler()
      
      try {
        // Create a simple FetchParams for testing
        val fetchParams = coursier.cli.params.FetchParams(
          resolve = coursier.cli.params.ResolveParams(),
          artifact = coursier.cli.params.ArtifactParams(),
          channel = coursier.cli.params.ChannelParams()
        )
        
        // Test that task can be created with custom handler factory
        val task = Fetch.task(
          fetchParams,
          pool,
          Seq("com.example:test-artifact:1.0.0"),
          customHandlerFactory = Some(customHandler)
        )
        
        // The task should be created successfully
        assert(task != null)
        
        // Note: We don't actually run the task since it would require a full repository setup
        // but this tests that the API accepts the custom handler factory parameter
        
      } finally {
        pool.shutdown()
      }
    }
  }
}