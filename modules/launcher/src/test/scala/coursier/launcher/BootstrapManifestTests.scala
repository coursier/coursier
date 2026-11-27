package coursier.launcher

import java.io.{ByteArrayOutputStream, File, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.jar.{JarFile, Manifest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import utest._

import scala.util.Try

object BootstrapManifestTests extends TestSuite {

  private def readFully(is: InputStream): Array[Byte] =
    try {
      val baos = new ByteArrayOutputStream
      val buf  = new Array[Byte](16384)
      var read = is.read(buf)
      while (read >= 0) {
        baos.write(buf, 0, read)
        read = is.read(buf)
      }
      baos.toByteArray
    }
    finally is.close()

  private def classBytes(name: String): Array[Byte] = {
    val is = getClass.getClassLoader.getResourceAsStream(name.replace('.', '/') + ".class")
    assert(is != null)
    readFully(is)
  }

  private def makeJar(dest: Path, classes: Seq[String]): Unit = {
    val zos = new ZipOutputStream(Files.newOutputStream(dest))
    try
      for (c <- classes) {
        zos.putNextEntry(new ZipEntry(c.replace('.', '/') + ".class"))
        zos.write(classBytes(c))
        zos.closeEntry()
      }
    finally zos.close()
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  private def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-bootstrap-manifest-")
    try f(dir)
    finally deleteRecursive(dir.toFile)
  }

  // Scala library JARs, passed as file: URLs, which bootstrap launchers don't check
  private def scalaLibraryUrls: Seq[String] =
    (Seq(classOf[Option[_]]) ++ Try(Class.forName("scala.runtime.Scala3RunTime")).toOption)
      .map(c =>
        Paths.get(c.getProtectionDomain.getCodeSource.getLocation.toURI).toUri.toASCIIString
      )
      .distinct

  private def manifest(jar: Path): Manifest = {
    val jf = new JarFile(jar.toFile)
    try jf.getManifest
    finally jf.close()
  }

  private def run(launcher: Path): (Int, String) = {
    val javaPath = Paths.get(sys.props("java.home"), "bin", "java").toString
    val pb       = new ProcessBuilder(javaPath, "-jar", launcher.toString)
    pb.redirectErrorStream(true)
    val proc   = pb.start()
    val output = new String(readFully(proc.getInputStream), StandardCharsets.UTF_8)
    (proc.waitFor(), output)
  }

  private val app = "manifesttest.OpensCheck"

  private def params(dir: Path): Parameters.Bootstrap = {
    val appJar = dir.resolve("app.jar")
    makeJar(appJar, Seq(app, app + "$"))
    Parameters.Bootstrap(
      Seq(
        ClassLoaderContent(
          (appJar.toUri.toASCIIString +: scalaLibraryUrls).map(ClassPathEntry.Url(_))
        )
      ),
      app
    ).copy(preambleOpt = None)
  }

  val tests = Tests {

    test("no attributes") {
      withTmpDir { dir =>
        val launcher = dir.resolve("launcher.jar")
        BootstrapGenerator.generate(params(dir), launcher)
        val (code, output) = run(launcher)
        assert(code == 0)
        assert(output.contains("java.lang opened: false"))
      }
    }

    test("attributes added to the manifest, and honored by java -jar") {
      withTmpDir { dir =>
        val launcher = dir.resolve("launcher.jar")
        val params0 = params(dir).copy(
          manifestAttributes = Seq(
            "Add-Opens" -> "java.base/java.lang",
            "X-Custom"  -> "value"
          )
        )
        BootstrapGenerator.generate(params0, launcher)

        val main = manifest(launcher).getMainAttributes
        assert(main.getValue("Main-Class") == "coursier.bootstrap.launcher.Launcher")
        assert(main.getValue("Add-Opens") == "java.base/java.lang")
        assert(main.getValue("X-Custom") == "value")

        val (code, output) = run(launcher)
        assert(code == 0)
        assert(output.contains("java.lang opened: true"))
      }
    }

    test("attributes added to a manifest passed as extra zip entry") {
      withTmpDir { dir =>
        val launcher = dir.resolve("launcher.jar")
        val userManifest =
          "Manifest-Version: 1.0\nMain-Class: coursier.bootstrap.launcher.Launcher\nX-User: a\n\n"
        val params0 = params(dir).copy(
          extraZipEntries = Seq(
            new ZipEntry(JarFile.MANIFEST_NAME) -> userManifest.getBytes(StandardCharsets.UTF_8)
          ),
          manifestAttributes = Seq("X-User" -> "b", "X-Custom" -> "value")
        )
        BootstrapGenerator.generate(params0, launcher)

        val main = manifest(launcher).getMainAttributes
        assert(main.getValue("X-User") == "b")
        assert(main.getValue("X-Custom") == "value")
        val (code, _) = run(launcher)
        assert(code == 0)
      }
    }

    test("Main-Class can't be overridden") {
      withTmpDir { dir =>
        val params0 = params(dir).copy(manifestAttributes = Seq("Main-Class" -> "foo.Main"))
        val ex = assertThrows[IllegalArgumentException] {
          BootstrapGenerator.generate(params0, dir.resolve("launcher.jar"))
        }
        assert(ex.getMessage.contains("Main-Class"))
      }
    }
  }
}
