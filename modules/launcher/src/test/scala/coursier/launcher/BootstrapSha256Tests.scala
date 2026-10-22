package coursier.launcher

import java.io.{ByteArrayOutputStream, File, InputStream}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import com.sun.net.httpserver.HttpServer
import utest._

import coursier.paths.CachePath

import scala.util.Try

object BootstrapSha256Tests extends TestSuite {

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

  private def entryOpt(jar: Path, name: String): Option[String] = {
    val zf = new ZipFile(jar.toFile)
    try
      Option(zf.getEntry(name)).map { e =>
        new String(readFully(zf.getInputStream(e)), StandardCharsets.UTF_8)
      }
    finally zf.close()
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  private def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-bootstrap-sha256-")
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

  private def withServer[T](f: (String, AtomicReference[Array[Byte]]) => T): T = {
    val served = new AtomicReference[Array[Byte]](Array.emptyByteArray)
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      exchange => {
        val b = served.get()
        exchange.sendResponseHeaders(200, b.length.toLong)
        val os = exchange.getResponseBody
        try os.write(b)
        finally os.close()
      }
    )
    server.start()
    try f(s"http://127.0.0.1:${server.getAddress.getPort}", served)
    finally server.stop(0)
  }

  private def run(launcher: Path, cache: Path): (Int, String) = {
    val javaPath = Paths.get(sys.props("java.home"), "bin", "java").toString
    val pb       = new ProcessBuilder(javaPath, "-jar", launcher.toString)
    pb.environment().put("COURSIER_CACHE", cache.toString)
    pb.redirectErrorStream(true)
    val proc   = pb.start()
    val output = new String(readFully(proc.getInputStream), StandardCharsets.UTF_8)
    (proc.waitFor(), output)
  }

  private val hello = "shadingtest.app.Hello"

  val tests = Tests {

    test("launcher checks downloaded and cached JARs") {
      withTmpDir { dir =>
        withServer { (base, served) =>
          val appJar = dir.resolve("app.jar")
          makeJar(appJar, Seq(hello, "shadingtest.app.Hello$", "shadingtest.lib.Greeter"))
          val appJarContent = Files.readAllBytes(appJar)
          val appUrl        = s"$base/test/app.jar"

          val cache  = dir.resolve("cache")
          val cached = CachePath.localFile(appUrl, cache.toFile, null, false).toPath

          val launcher = dir.resolve("launcher.jar")
          val params = Parameters.Bootstrap(
            Seq(
              ClassLoaderContent(
                ClassPathEntry.Url(appUrl, Some(ClassPathEntry.sha256(appJar))) +:
                  scalaLibraryUrls.map(ClassPathEntry.Url(_))
              )
            ),
            hello
          ).copy(preambleOpt = None)
          BootstrapGenerator.generate(params, launcher)

          val sha256Lines =
            entryOpt(launcher, "coursier/bootstrap/launcher/bootstrap-jar-sha256")
              .toSeq
              .flatMap(_.split("\n", -1).toSeq)
          assert(sha256Lines.length == 1 + scalaLibraryUrls.length)
          assert(sha256Lines.head == ClassPathEntry.sha256(appJar))
          assert(sha256Lines.tail.forall(_.isEmpty))

          // tampered with download: rejected, not put in cache
          served.set("tampered with".getBytes(StandardCharsets.UTF_8))
          val (code0, output0) = run(launcher, cache)
          assert(code0 != 0)
          assert(output0.contains("SHA-256 mismatch"))
          assert(!Files.exists(cached))

          // genuine download
          served.set(appJarContent)
          val (code1, output1) = run(launcher, cache)
          assert(code1 == 0)
          assert(output1.contains("Hello, world!"))
          assert(Files.exists(cached))

          // tampered with cache
          Files.write(cached, "tampered with".getBytes(StandardCharsets.UTF_8))
          val (code2, output2) = run(launcher, cache)
          assert(code2 != 0)
          assert(output2.contains("SHA-256 mismatch"))
          assert(output2.contains(appUrl))
        }
      }
    }

    test("no digest entry without digests") {
      withTmpDir { dir =>
        val launcher = dir.resolve("launcher.jar")
        val params = Parameters.Bootstrap(
          Seq(ClassLoaderContent.fromUrls(Seq("https://repo.invalid/a.jar"))),
          hello
        ).copy(preambleOpt = None)
        BootstrapGenerator.generate(params, launcher)
        assert(entryOpt(launcher, "coursier/bootstrap/launcher/bootstrap-jar-urls").nonEmpty)
        assert(entryOpt(launcher, "coursier/bootstrap/launcher/bootstrap-jar-sha256").isEmpty)
      }
    }

    test("launcher without digests still runs") {
      withTmpDir { dir =>
        val appJar = dir.resolve("app.jar")
        makeJar(appJar, Seq(hello, "shadingtest.app.Hello$", "shadingtest.lib.Greeter"))
        val appUrl = "https://repo.invalid/test/app.jar"
        val cache  = dir.resolve("cache")
        val cached = CachePath.localFile(appUrl, cache.toFile, null, false).toPath
        Files.createDirectories(cached.getParent)
        Files.copy(appJar, cached)

        val launcher = dir.resolve("launcher.jar")
        val params = Parameters.Bootstrap(
          Seq(ClassLoaderContent.fromUrls(appUrl +: scalaLibraryUrls)),
          hello
        ).copy(preambleOpt = None)
        BootstrapGenerator.generate(params, launcher)

        val (code, output) = run(launcher, cache)
        assert(code == 0)
        assert(output.contains("Hello, world!"))
      }
    }
  }

}
