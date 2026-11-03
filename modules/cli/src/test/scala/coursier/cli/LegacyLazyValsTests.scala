package coursier.cli

import java.io.{ByteArrayInputStream, File, InputStream}
import java.util.zip.ZipFile

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.objectweb.asm.{ClassReader, ClassVisitor, MethodVisitor, Opcodes}
import utest._

/** Checks that the CLI class path still has lazy vals going through `sun.misc.Unsafe`
  *
  * Code compiled with Scala 3 before 3.8 (without `-Yfuture-lazy-vals`) gets the offsets of lazy
  * val fields with `scala.runtime.LazyVals$.getOffsetStatic` (or `getOffset`, before 3.3), and
  * updates them with `sun.misc.Unsafe`, that warns about it from JDK 24 on. The native launchers
  * are built with `-Dsun.misc.unsafe.memory.access=allow` (see the cli `native-image.properties`)
  * and the JAR launchers are run with it (see `jarLauncherArgs` in `build.mill`) to silence these
  * warnings. Once no class calls these methods any more, this test fails, and these properties can
  * be removed (along with this test).
  */
object LegacyLazyValsTests extends TestSuite {

  private lazy val classPath: Seq[File] =
    sys.props
      .getOrElse(
        "coursier-test.cli-class-path",
        sys.error("coursier-test.cli-class-path Java property not set")
      )
      .split(File.pathSeparator)
      .toSeq
      .filter(_.nonEmpty)
      .map(new File(_))

  private val lazyValsClass = "scala/runtime/LazyVals$"
  private val offsetMethods = Set("getOffset", "getOffsetStatic")

  /** Whether a class file calls `LazyVals$.getOffset` or `LazyVals$.getOffsetStatic` */
  private def usesLegacyLazyVals(is: InputStream): Boolean = {
    var found = false
    val methodVisitor = new MethodVisitor(Opcodes.ASM9) {
      override def visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
      ): Unit =
        if (owner == lazyValsClass && offsetMethods(name))
          found = true
    }
    val classVisitor = new ClassVisitor(Opcodes.ASM9) {
      override def visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String,
        exceptions: Array[String]
      ): MethodVisitor =
        methodVisitor
    }
    new ClassReader(is).accept(classVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
    found
  }

  private def isLazyValsRuntime(path: String): Boolean =
    path.startsWith("scala/runtime/LazyVals")

  /** The classes of a class path entry calling the legacy lazy val methods */
  private def legacyLazyValClasses(entry: File): Seq[String] =
    if (entry.isDirectory) {
      val base = os.Path(entry.getAbsoluteFile)
      os.walk(base)
        .filter(p => p.last.endsWith(".class") && os.isFile(p))
        .map(_.relativeTo(base).toString)
        .filter(path => !isLazyValsRuntime(path))
        .filter { path =>
          usesLegacyLazyVals(new ByteArrayInputStream(os.read.bytes(base / os.RelPath(path))))
        }
    }
    else if (entry.getName.endsWith(".jar") && entry.isFile)
      Using.resource(new ZipFile(entry)) { zf =>
        zf.entries()
          .asScala
          .filter(e => !e.isDirectory && e.getName.endsWith(".class"))
          .filter(e => !isLazyValsRuntime(e.getName))
          .filter(e => Using.resource(zf.getInputStream(e))(usesLegacyLazyVals))
          .map(_.getName)
          .toVector
      }
    else
      Nil

  val tests = Tests {
    test("class path still has Unsafe-based lazy vals") {
      assert(classPath.nonEmpty)
      val found = classPath
        .map(entry => entry -> legacyLazyValClasses(entry))
        .filter(_._2.nonEmpty)
      for ((entry, classes) <- found) {
        System.err.println(s"${entry.getName}: ${classes.length} class(es) with legacy lazy vals")
        for (cls <- classes.sorted.take(5))
          System.err.println(s"  $cls")
        if (classes.length > 5)
          System.err.println("  …")
      }
      // If this fails, nothing in the CLI class path uses sun.misc.Unsafe for lazy vals any more:
      // remove -Dsun.misc.unsafe.memory.access=allow from the cli native-image.properties and
      // from jarLauncherArgs in build.mill, then remove this test.
      assert(found.nonEmpty)
      found.map { case (entry, classes) => s"${entry.getName} (${classes.length})" }
    }
  }
}
