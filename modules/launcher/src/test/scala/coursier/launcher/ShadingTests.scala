package coursier.launcher

import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import utest._

object ShadingTests extends TestSuite {

  private def classBytes(name: String): Array[Byte] = {
    val resourceName = name.replace('.', '/') + ".class"
    val is           = getClass.getClassLoader.getResourceAsStream(resourceName)
    assert(is != null)
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
  }

  /** Builds a JAR containing the `.class` files of the passed classes, read from the test
    * classpath.
    */
  private def makeJar(classes: Seq[String]): Path = {
    val jar = Files.createTempFile("shading-input-", ".jar")
    val zos = new ZipOutputStream(Files.newOutputStream(jar))
    try
      for (c <- classes) {
        zos.putNextEntry(new ZipEntry(c.replace('.', '/') + ".class"))
        zos.write(classBytes(c))
        zos.closeEntry()
      }
    finally zos.close()
    jar
  }

  private def entries(jar: Path): Set[String] = {
    val zf = new ZipFile(jar.toFile)
    try {
      val b  = Set.newBuilder[String]
      val it = zf.entries()
      while (it.hasMoreElements)
        b += it.nextElement().getName
      b.result()
    }
    finally zf.close()
  }

  private def withTempFile[T](prefix: String)(f: Path => T): T = {
    val p = Files.createTempFile(prefix, ".jar")
    Files.deleteIfExists(p)
    try f(p)
    finally Files.deleteIfExists(p)
  }

  private val greeter = "shadingtest.lib.Greeter"
  private val hello   = "shadingtest.app.Hello"

  val tests = Tests {

    test("Shading.shadeJars relocates classes") {
      val input = makeJar(Seq(greeter, hello))
      try
        withTempFile("shading-output-") { output =>
          Shading.shadeJars(
            Seq(input.toFile),
            output,
            Seq(ShadingRule.rename("shadingtest.lib", "shaded.shadingtest.lib"))
          )
          val names = entries(output)
          assert(names.contains("shaded/shadingtest/lib/Greeter.class"))
          assert(!names.contains("shadingtest/lib/Greeter.class"))
          // classes that are not relocated keep their location
          assert(names.contains("shadingtest/app/Hello.class"))
        }
      finally Files.deleteIfExists(input)
    }

    test("moveUnder relocation") {
      val input = makeJar(Seq(greeter))
      try
        withTempFile("shading-moveunder-") { output =>
          Shading.shadeJars(
            Seq(input.toFile),
            output,
            Seq(ShadingRule.moveUnder("shadingtest", "shaded"))
          )
          val names = entries(output)
          assert(names.contains("shaded/shadingtest/lib/Greeter.class"))
          assert(!names.contains("shadingtest/lib/Greeter.class"))
        }
      finally Files.deleteIfExists(input)
    }

    test("AssemblyGenerator applies shading rules and rewrites references") {
      // hello is a Scala object, whose implementation lives in the `Hello$` class
      val input = makeJar(Seq(greeter, hello, hello + "$"))
      try
        withTempFile("shading-assembly-") { output =>
          val params = Parameters.Assembly()
            .withFiles(Seq(input.toFile))
            .withMainClass("shaded.shadingtest.app.Hello")
            .withPreambleOpt(None)
            .withShadingRules(Seq(
              ShadingRule.rename("shadingtest.lib", "shaded.shadingtest.lib"),
              ShadingRule.rename("shadingtest.app", "shaded.shadingtest.app")
            ))

          Generator.generate(params, output)

          val names = entries(output)
          assert(names.contains("shaded/shadingtest/lib/Greeter.class"))
          assert(names.contains("shaded/shadingtest/app/Hello.class"))
          assert(!names.contains("shadingtest/lib/Greeter.class"))
          assert(!names.contains("shadingtest/app/Hello.class"))

          // The relocated app references the relocated lib: load it and run it to make sure the
          // generated classes are valid and that references were rewritten.
          val loader = new URLClassLoader(Array(output.toUri.toURL), getClass.getClassLoader)
          try {
            val cls    = Class.forName("shaded.shadingtest.app.Hello$", true, loader)
            val module = cls.getField("MODULE$").get(null)
            val result = cls
              .getMethod("message", classOf[String])
              .invoke(module, "world")
            assert(result == "Hello, world!")
          }
          finally loader.close()
        }
      finally Files.deleteIfExists(input)
    }
  }
}
