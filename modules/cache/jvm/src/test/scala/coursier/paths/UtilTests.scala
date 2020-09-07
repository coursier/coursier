package coursier.paths

import java.io.File
import java.nio.file.{Files, Path}
import java.util.Properties

import utest._

import scala.collection.JavaConverters._

object UtilTests extends TestSuite {

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    if (f.exists())
      f.delete()
  }

  val tests = Tests {
    "createDirectories fine with sym links" - {
      var tmpDir: Path = null
      try {
        tmpDir = Files.createTempDirectory("coursier-paths-tests")
        val dir = Files.createDirectories(tmpDir.resolve("dir"))
        val link = Files.createSymbolicLink(tmpDir.resolve("link"), dir)
        Util.createDirectories(link) // should not throw
      } finally {
        deleteRecursive(tmpDir.toFile)
      }
    }

    "property expansion" - {
      "simple" - {
        val map = Map("something" -> "value", "other" -> "a")
        val sysProps = new Properties
        sysProps.setProperty("foo", "FOO")
        val toSet = Util.expandProperties(sysProps, map.asJava)
          .asScala
          .toVector
          .sorted
        val expected = map.toVector.sorted
        assert(toSet == expected)
      }

      "substitution" - {
        val map = Map("something" -> "value ${foo}", "other" -> "a")
        val sysProps = new Properties
        sysProps.setProperty("foo", "FOO")
        val toSet = Util.expandProperties(sysProps, map.asJava)
          .asScala
          .toVector
          .sorted
        val expected = Seq("something" -> "value FOO", "other" -> map("other")).sorted
        assert(toSet == expected)
      }

      "optional value" - {
        val map = Map("something" -> "value", "foo?" -> "A")
        val sysProps = new Properties
        sysProps.setProperty("foo", "FOO")
        val toSet = Util.expandProperties(sysProps, map.asJava)
          .asScala
          .toVector
          .sorted
        val expected = Seq("something" -> "value")
        assert(toSet == expected)
      }
    }
  }

}
