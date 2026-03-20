package coursier.env

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.google.common.jimfs.{Configuration, Jimfs}
import utest._

object WindowsEnvVarUpdaterTests extends TestSuite {

  private val archiveCache = "C:\\Users\\alex\\AppData\\Local\\Coursier\\cache\\arc"

  private def jvmBinDir(id: String): String =
    archiveCache + "\\https\\github.com\\adoptium\\" + id + "\\bin"

  // Pretends every directory under the archive cache is a JVM bin directory, so that the
  // entry filtering can be checked without hitting the file system.
  private val jvmBinDirsOnly: Path => Boolean =
    path => path.toString.toLowerCase.startsWith(archiveCache.toLowerCase)

  private def writeJvm(
    dir: Path,
    javaExtension: String = ".exe",
    releaseContent: Option[String] = Some("JAVA_VERSION=\"17.0.10\"\nOS_ARCH=\"x86_64\"\n")
  ): Path = {
    val binDir = dir.resolve("bin")
    Files.createDirectories(binDir)
    Files.write(binDir.resolve("java" + javaExtension), Array.emptyByteArray)
    for (content <- releaseContent)
      Files.write(dir.resolve("release"), content.getBytes(StandardCharsets.UTF_8))
    binDir
  }

  val tests = Tests {

    test("splitPathLike") {
      test("empty") {
        assert(WindowsEnvVarUpdater.splitPathLike(None).isEmpty)
        assert(WindowsEnvVarUpdater.splitPathLike(Some("")).isEmpty)
      }
      test("round trip") {
        val value = "C:\\Windows;C:\\Windows\\system32;%JAVA_HOME%\\bin"
        val entries = WindowsEnvVarUpdater.splitPathLike(Some(value))
        assert(entries.length == 3)
        assert(WindowsEnvVarUpdater.joinPathLike(entries) == value)
      }
      test("keep empty entries") {
        // an empty PATH entry means the current directory, dropping it silently would
        // change the meaning of the PATH
        val entries = WindowsEnvVarUpdater.splitPathLike(Some("C:\\Windows;;C:\\other"))
        assert(entries == Seq("C:\\Windows", "", "C:\\other"))
      }
    }

    test("isJavaHomeBinDir") {
      val javaHome = Some("C:\\jvm\\17")
      assert(WindowsEnvVarUpdater.isJavaHomeBinDir("PATH", "C:\\jvm\\17\\bin", javaHome))
      // JavaHome.environmentFor builds that entry with File.separator, which can be either
      assert(WindowsEnvVarUpdater.isJavaHomeBinDir("PATH", "C:\\jvm\\17/bin", javaHome))
      assert(WindowsEnvVarUpdater.isJavaHomeBinDir("PATH", "c:\\JVM\\17\\BIN", javaHome))
      assert(!WindowsEnvVarUpdater.isJavaHomeBinDir("PATH", "C:\\jvm\\21\\bin", javaHome))
      assert(!WindowsEnvVarUpdater.isJavaHomeBinDir("PATH", "C:\\jvm\\17\\bin", None))
      // only the PATH gets that treatment
      assert(!WindowsEnvVarUpdater.isJavaHomeBinDir("OTHER", "C:\\jvm\\17\\bin", javaHome))
    }

    test("appended") {
      val entries = Seq("C:\\Windows", "C:\\Users\\alex\\AppData\\Local\\Coursier\\data\\bin")
      test("new entry") {
        val expected = entries :+ "C:\\other"
        assert(WindowsEnvVarUpdater.appended(entries, "C:\\other") == expected)
      }
      test("already there") {
        assert(WindowsEnvVarUpdater.appended(entries, "C:\\Windows") == entries)
      }
    }

    test("removed") {
      val entries = Seq("C:\\Windows", "C:\\jvm\\17\\bin", "%JAVA_HOME%\\bin")
      val removed = WindowsEnvVarUpdater.removed(entries, Seq("c:\\JVM\\17\\bin", "%java_home%\\BIN"))
      assert(removed == Seq("C:\\Windows"))
    }

    test("withJavaHomeBinRef") {
      test("replace the former JVM bin directory") {
        val entries = Seq("C:\\Windows", jvmBinDir("17.0.10"))
        val updated = WindowsEnvVarUpdater.withJavaHomeBinRef(
          entries,
          jvmBinDir("21.0.2"),
          Some(jvmBinDir("17.0.10").stripSuffix("\\bin"))
        )
        assert(updated == Seq("C:\\Windows", WindowsEnvVarUpdater.javaHomeBinRef))
      }
      test("idempotent") {
        val entries = Seq("C:\\Windows", WindowsEnvVarUpdater.javaHomeBinRef)
        val updated = WindowsEnvVarUpdater.withJavaHomeBinRef(
          entries,
          jvmBinDir("21.0.2"),
          Some(jvmBinDir("21.0.2").stripSuffix("\\bin"))
        )
        assert(updated == entries)
      }
      test("no former JAVA_HOME") {
        val entries = Seq("C:\\Windows")
        val updated =
          WindowsEnvVarUpdater.withJavaHomeBinRef(entries, jvmBinDir("21.0.2"), None)
        assert(updated == Seq("C:\\Windows", WindowsEnvVarUpdater.javaHomeBinRef))
      }
    }

    test("withoutJvmBinDirsUnder") {
      test("clean up piled up JVM bin directories") {
        val entries = Seq(
          "C:\\Windows",
          jvmBinDir("17.0.10"),
          jvmBinDir("17.0.14"),
          jvmBinDir("11.0.22"),
          "C:\\Users\\alex\\AppData\\Local\\Coursier\\data\\bin"
        )
        val updated =
          WindowsEnvVarUpdater.withoutJvmBinDirsUnder(entries, archiveCache, jvmBinDirsOnly)
        assert(
          updated == Seq(
            "C:\\Windows",
            "C:\\Users\\alex\\AppData\\Local\\Coursier\\data\\bin"
          )
        )
      }
      test("keep entries that aren't JVM bin directories") {
        val entries = Seq("C:\\Windows", archiveCache + "\\not-a-jvm")
        val updated =
          WindowsEnvVarUpdater.withoutJvmBinDirsUnder(entries, archiveCache, _ => false)
        assert(updated == entries)
      }
      test("ignore case and separators") {
        val entries = Seq(jvmBinDir("17.0.10").toUpperCase.replace('\\', '/'))
        val updated =
          WindowsEnvVarUpdater.withoutJvmBinDirsUnder(entries, archiveCache + "\\", _ => true)
        assert(updated.isEmpty)
      }
      test("don't remove the prefix directory itself") {
        val entries = Seq(archiveCache)
        val updated =
          WindowsEnvVarUpdater.withoutJvmBinDirsUnder(entries, archiveCache, _ => true)
        assert(updated == entries)
      }
      test("keep empty entries") {
        val entries = Seq("", "C:\\Windows")
        val updated =
          WindowsEnvVarUpdater.withoutJvmBinDirsUnder(entries, archiveCache, _ => true)
        assert(updated == entries)
      }
    }

    test("isJvmBinDir") {
      val fs = Jimfs.newFileSystem(Configuration.windows())
      val root = fs.getPath("C:\\jvm")
      val extensions = Seq(".exe", ".cmd", ".com", ".bat")

      test("JVM") {
        val binDir = writeJvm(root.resolve("17"))
        assert(WindowsEnvVarUpdater.isJvmBinDir(binDir, extensions))
      }
      test("java executable with another extension") {
        val binDir = writeJvm(root.resolve("17-bat"), javaExtension = ".bat")
        assert(WindowsEnvVarUpdater.isJvmBinDir(binDir, extensions))
        assert(!WindowsEnvVarUpdater.isJvmBinDir(binDir, Seq(".exe")))
      }
      test("no java executable") {
        val binDir = writeJvm(root.resolve("17-no-java"))
        Files.delete(binDir.resolve("java.exe"))
        assert(!WindowsEnvVarUpdater.isJvmBinDir(binDir, extensions))
      }
      test("no release file") {
        val binDir = writeJvm(root.resolve("17-no-release"), releaseContent = None)
        assert(!WindowsEnvVarUpdater.isJvmBinDir(binDir, extensions))
      }
      test("release file with no JAVA_VERSION") {
        val binDir =
          writeJvm(root.resolve("17-other-release"), releaseContent = Some("OS_ARCH=\"x86_64\"\n"))
        assert(!WindowsEnvVarUpdater.isJvmBinDir(binDir, extensions))
      }
      test("no parent directory") {
        assert(!WindowsEnvVarUpdater.isJvmBinDir(fs.getPath("C:\\"), extensions))
      }
    }
  }
}
