package coursier.jvm

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicBoolean

import coursier.cache.internal.FileUtil
import coursier.cache.{ArchiveCache, MockCache}
import coursier.util.{Sync, Task}
import utest._

import scala.sys.process._
import java.io.IOException
import scala.util.Success
import scala.util.Failure
import scala.util.Properties
import scala.util.Try

object JvmCacheTests extends TestSuite {

  val theOS    = if (Properties.isWin) "windows" else "the-os"
  val filename = if (Properties.isWin) "java.bat" else "java"

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTempDir[T](f: Path => T): T = {
    var dir: Path = null
    try {
      dir = Files.createTempDirectory("jvm-cache-tests-")
      f(dir)
    }
    finally if (dir != null)
      deleteRecursive(dir.toFile)
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

  val mockDataLocation = {
    val dir = Paths.get("modules/jvm/src/test/resources/mock-cache")
    assert(Files.isDirectory(dir))
    dir
  }

  val tests = Tests {
    test("simple") {
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
          |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1-macos.tar.gz",
          |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2.tar.gz"
          |      }
          |    }
          |  },
          |  "windows": {
          |    "the-arch": {
          |      "jdk@the-jdk": {
          |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1-windows.tar.gz",
          |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2-windows.tar.gz"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val index = JvmIndex.fromString(strIndex).fold(throw _, identity)

      val cache = MockCache.create[Task](mockDataLocation, pool)

      test("specific version") {
        withTempDir { tmpDir =>
          val archiveCache = ArchiveCache[Task](tmpDir.toFile).withCache(cache)
          val jvmCache = JvmCache()
            .withArchiveCache(archiveCache)
            .withOs(theOS)
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home           = jvmCache.get("the-jdk:1.1").unsafeRun()(cache.ec)
          val expectedOutput = "the jdk 1.1\n"
          val javaExec       = new File(new File(home, "bin"), filename)

          val output = Seq(javaExec.getAbsolutePath, "-version").!!
          assert(output.replace("\r\n", "\n") == expectedOutput)
        }
      }

      test("version range") {
        withTempDir { tmpDir =>
          val archiveCache = ArchiveCache[Task](tmpDir.toFile).withCache(cache)
          val jvmCache = JvmCache()
            .withArchiveCache(archiveCache)
            .withOs(theOS)
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home           = jvmCache.get("the-jdk:1+").unsafeRun()(cache.ec)
          val javaExec       = new File(new File(home, "bin"), filename)
          val output         = Seq(javaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.2\n"
          assert(output.replace("\r\n", "\n") == expectedOutput)
        }
      }

      test("Contents/Home directory on macOS") {
        withTempDir { tmpDir =>
          val archiveCache = ArchiveCache[Task](tmpDir.toFile).withCache(cache)
          val jvmCache = JvmCache()
            .withArchiveCache(archiveCache)
            .withOs("darwin")
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home = jvmCache.get("the-jdk:1.1").unsafeRun()(cache.ec)
          assert(home.getName == "Home")
          assert(home.getParentFile.getName == "Contents")
          val javaExec = new File(home, "bin/java")
          try {
            val output         = Seq(javaExec.getAbsolutePath, "-version").!!
            val expectedOutput = "the jdk 1.1\n"
            assert(output == expectedOutput)
            ()
          }
          catch {
            case _: IOException if Properties.isWin => ()
          }

          val alreadyThereHome = jvmCache
            .getIfInstalled("the-jdk:1.1")
            .unsafeRun()(cache.ec)
            .getOrElse {
              sys.error("Should have been there")
            }
          assert(alreadyThereHome.getName == "Home")
          assert(alreadyThereHome.getParentFile.getName == "Contents")
          val alreadyThereJavaExec = new File(alreadyThereHome, "bin/java")
          try {
            val output         = Seq(alreadyThereJavaExec.getAbsolutePath, "-version").!!
            val expectedOutput = "the jdk 1.1\n"
            assert(output == expectedOutput)
            ()
          }
          catch {
            case _: IOException if Properties.isWin => ()
          }
        }
      }

      test("no Contents/Home directory on macOS") {
        withTempDir { tmpDir =>
          val archiveCache = ArchiveCache[Task](tmpDir.toFile).withCache(cache)
          val jvmCache = JvmCache()
            .withArchiveCache(archiveCache)
            .withOs("darwin")
            .withArchitecture("the-arch")
            .withDefaultJdkNameOpt(None)
            .withDefaultVersionOpt(None)
            .withIndex(Task.point(index))

          val home = jvmCache.get("the-jdk:1.2").unsafeRun()(cache.ec)
          assert(home.getName == "the-jdk-1.2")
          val javaExec = new File(home, "bin/java")
          try {
            val output         = Seq(javaExec.getAbsolutePath, "-version").!!
            val expectedOutput = "the jdk 1.2\n"
            assert(output == expectedOutput)
            ()
          }
          catch {
            case _: IOException if Properties.isWin => ()
          }
        }
      }
    }
  }
}
