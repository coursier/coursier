package coursier.jvm

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicBoolean

import coursier.cache.internal.FileUtil
import coursier.cache.MockCache
import coursier.util.{Sync, Task}
import utest._

import scala.sys.process._

object JvmCacheTests extends TestSuite {

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  private def withTempDir[T](prefix: String)(f: Path => T): T = {
    var dir: Path = null
    try {
      dir = Files.createTempDirectory(prefix)
      f(dir)
    } finally {
      if (dir != null)
        deleteRecursive(dir.toFile)
    }
  }

  private val poolInitialized = new AtomicBoolean(false)
  private lazy val pool = {
    val p = Sync.fixedThreadPool(6)
    poolInitialized.set(true)
    p
  }

  override def utestAfterAll(): Unit =
    if (poolInitialized.getAndSet(false))
      pool.shutdown()

  private val mockDataLocation = {
    val dir = Paths.get("modules/jvm/src/test/resources/mock-cache")
    assert(Files.isDirectory(dir))
    dir
  }

  val tests = Tests {
    "simple" - {
      val strIndex =
        """{
          |  "the-os": {
          |    "the-arch": {
          |      "jdk@the-jdk": {
          |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1.tar.gz",
          |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2.tar.gz"
          |      }
          |    }
          |  },
          |  "darwin": {
          |    "the-arch": {
          |      "jdk@the-jdk": {
          |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1-macos.tar.gz"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val index = JvmIndex.fromString(strIndex).fold(throw _, identity)

      val cache = MockCache.create[Task](mockDataLocation, pool)

      "specific version" - {
        withTempDir("jvm-cache-tests-") { tmpDir =>
          val jvmCache = JvmCache()
            .withBaseDirectory(tmpDir.toFile)
            .withCache(cache)
            .withOs("the-os")
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home = jvmCache.get("the-jdk:1.1").unsafeRun()(cache.ec)
          val javaExec = new File(home, "bin/java")
          val output = Seq(javaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.1\n"
          assert(output == expectedOutput)
        }
      }

      "version range" - {
        withTempDir("jvm-cache-tests-") { tmpDir =>
          val jvmCache = JvmCache()
            .withBaseDirectory(tmpDir.toFile)
            .withCache(cache)
            .withOs("the-os")
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home = jvmCache.get("the-jdk:1+").unsafeRun()(cache.ec)
          val javaExec = new File(home, "bin/java")
          val output = Seq(javaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.2\n"
          assert(output == expectedOutput)
        }
      }

      "Contents/Home directory on macOS" - {
        withTempDir("jvm-cache-tests-") { tmpDir =>
          val jvmCache = JvmCache()
            .withBaseDirectory(tmpDir.toFile)
            .withCache(cache)
            .withOs("darwin")
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home = jvmCache.get("the-jdk:1.1").unsafeRun()(cache.ec)
          assert(home.getName == "Home")
          assert(home.getParentFile.getName == "Contents")
          val javaExec = new File(home, "bin/java")
          val output = Seq(javaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.1\n"
          assert(output == expectedOutput)
        }
      }
    }
  }
}
