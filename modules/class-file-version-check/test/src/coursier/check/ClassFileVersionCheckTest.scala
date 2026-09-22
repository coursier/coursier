package coursier.check

import utest._

import java.io.File
import java.util.zip.ZipFile

import scala.jdk.CollectionConverters._
import scala.util.Using

object ClassFileVersionCheckTest extends TestSuite {

  def classFileVersionOffset = 44

  lazy val defaultMaxAllowedJvm = sys.props
    .getOrElse(
      "coursier.class-file-version-check.max-jvm",
      sys.error("Java property coursier.class-file-version-check.max-jvm not set")
    )
    .toInt

  val tests = Tests {
    test("check") {
      val jarsStr = sys.props.getOrElse(
        "coursier.class-file-version-check.jars",
        sys.error("Java property coursier.class-file-version-check.jars not set")
      )
      val jarEntries = jarsStr
        .split(File.pathSeparatorChar)
        .filter(_.nonEmpty)
        .map { entry =>
          if (entry.endsWith(")")) {
            val parenIdx = entry.lastIndexOf('(')
            val path     = entry.substring(0, parenIdx)
            val jvmVer   = entry.substring(parenIdx + 1, entry.length - 1).toInt
            (os.Path(path), jvmVer)
          }
          else
            (os.Path(entry), defaultMaxAllowedJvm)
        }

      assert(jarEntries.nonEmpty)

      val classOverridesPrefix = os.sub / "META-INF/versions"

      val violations = jarEntries.toSeq.flatMap {
        case (jarFile, jarMaxAllowed) =>
          Using.resource(new ZipFile(jarFile.toIO)) { zf =>
            zf.entries().asScala
              .filter(_.getName.endsWith(".class"))
              .flatMap { entry =>
                val subPath       = os.SubPath(entry.getName)
                val maxAllowedJvm =
                  if (subPath.startsWith(classOverridesPrefix))
                    subPath.segments.drop(classOverridesPrefix.segments.length).head.toInt
                  else
                    jarMaxAllowed

                val maxAllowed = maxAllowedJvm + classFileVersionOffset
                // sanity checks
                assert(maxAllowed > classFileVersionOffset)
                assert(maxAllowed < 30 + classFileVersionOffset)

                val major = majorFromClassBytes(zf.getInputStream(entry).readAllBytes())
                if (major > maxAllowed)
                  Seq(s"$jarFile!${entry.getName}: class file version $major > $maxAllowed")
                else
                  Nil
              }
              .toList
          }
      }

      if (violations.nonEmpty)
        throw new Exception(
          violations.mkString("Class file version violations:\n  ", "\n  ", "")
        )
    }
  }

  def majorFromClassBytes(bytes: Array[Byte]): Int = {
    import org.objectweb.asm.{ClassReader, ClassVisitor, Opcodes}
    val reader = new ClassReader(bytes)
    var major  = 0
    reader.accept(
      new ClassVisitor(Opcodes.ASM9) {
        override def visit(
          version: Int,
          access: Int,
          name: String,
          signature: String,
          superName: String,
          interfaces: Array[String]
        ) =
          major = version & 0xffff
      },
      ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
    )
    major
  }
}
