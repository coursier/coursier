package coursier.bootstrap.launcher

import java.io.File
import java.nio.file.{Files, Path}

import utest._

object CsLookupTests extends TestSuite {

  private def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("cs-lookup-tests")
    try f(dir)
    finally
      Files.walk(dir)
        .sorted(java.util.Comparator.reverseOrder[Path]())
        .forEach(p => Files.delete(p))
  }

  private def executable(dir: Path, name: String): Path = {
    val f = dir.resolve(name)
    Files.createDirectories(dir)
    Files.write(f, Array.emptyByteArray)
    f.toFile.setExecutable(true)
    f
  }

  private def pathOf(dirs: String*): String =
    dirs.mkString(File.pathSeparator)

  val tests = Tests {
    test("find in absolute entry") {
      withTmpDir { tmp =>
        val bin = tmp.resolve("bin")
        val cs  = executable(bin, "cs")
        val res = Config.findInPath("cs", pathOf(bin.toString), null, false)
        assert(res == cs.toAbsolutePath.toString)
      }
    }

    test("skip relative and empty entries") {
      withTmpDir { tmp =>
        executable(tmp, "cs")
        val bin = tmp.resolve("bin")
        val cs  = executable(bin, "cs")
        // relative entries pointing at a directory that contains a cs file
        val cwd    = new File(".").getCanonicalFile.toPath
        val relTmp = cwd.relativize(tmp).toString
        val res =
          Config.findInPath("cs", pathOf("", ".", relTmp, bin.toString), null, false)
        assert(res == cs.toAbsolutePath.toString)
        val res0 = Config.findInPath("cs", pathOf("", ".", relTmp), null, false)
        assert(res0 == null)
      }
    }

    test("skip directories") {
      withTmpDir { tmp =>
        Files.createDirectories(tmp.resolve("a/cs"))
        val bin = tmp.resolve("b")
        val cs  = executable(bin, "cs")
        val res =
          Config.findInPath("cs", pathOf(tmp.resolve("a").toString, bin.toString), null, false)
        assert(res == cs.toAbsolutePath.toString)
      }
    }

    test("windows extensions") {
      withTmpDir { tmp =>
        val a = tmp.resolve("a")
        executable(a, "cs")
        val b  = tmp.resolve("b")
        val cs = executable(b, "cs.bat")
        // file system lookups are case-sensitive here, unlike on Windows
        val exts = pathOf(".exe", "", ".bat")
        val res =
          Config.findInPath("cs", pathOf(a.toString, b.toString), exts, true)
        assert(res == cs.toAbsolutePath.toString)
        assert(Config.findInPath("cs", pathOf(a.toString, b.toString), null, true) == null)
      }
    }

    test("not found") {
      assert(Config.findInPath("cs", null, null, false) == null)
      assert(Config.findInPath("cs", "", null, false) == null)
    }
  }
}
