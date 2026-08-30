package coursierbuild

import org.objectweb.asm.ClassReader

import java.io.File
import java.util.zip.ZipFile

import scala.jdk.CollectionConverters.*

object Check {

  /** Checks that `jar` only contains classes and resources under the `ns` namespace */
  def onlyNamespace(ns: String, jar: File): Unit = {
    val zf = new ZipFile(jar)
    val unrecognized = zf.entries()
      .asScala
      .map(_.getName)
      .filter { n =>
        !n.startsWith("META-INF/") && !n.startsWith(ns + "/") &&
        n != "scala-collection-compat.properties" && // collection-compat adds that
        !n.contains("/libzstd-jni-") // com.github.luben:zstd-jni stuff (pulled via plexus-archiver)
      }
      .toVector
      .sorted
    for (u <- unrecognized)
      System.err.println(s"Unrecognized: $u")
    assert(unrecognized.isEmpty)
  }

  /** String constants of a class file (its `CONSTANT_String` constant pool entries) */
  private def stringConstants(classFile: Array[Byte]): Seq[String] = {
    val constantStringTag = 8 // CONSTANT_String, see JVMS 4.4
    val reader            = new ClassReader(classFile)
    val buffer            = Array.ofDim[Char](reader.getMaxStringLength)
    (1 until reader.getItemCount).flatMap { idx =>
      val offset = reader.getItem(idx)
      // CONSTANT_Long / CONSTANT_Double take two constant pool entries, the second one has a zero offset
      if (offset > 0 && reader.readByte(offset - 1) == constantStringTag)
        Seq(reader.readUTF8(offset, buffer))
      else
        Nil
    }
  }

  /** Ensures no coursier system property was shaded
    *
    * JarJar remaps string constants that look like class names, which would rename coursier system
    * properties like `coursier.repositories` too, see
    * [[https://github.com/coursier/interface/issues/477]]. Those are meant to be kept as is by the
    * identity rules built from [[CoursierProperties.list]].
    */
  def noShadedProperties(jar: File): Unit = {
    // properties can't be told apart from packages for sure, we assume a string whose last
    // element starts with a lower case letter is one (class names start with an upper case one)
    val propertyLike = "coursierapi\\.shaded\\.coursier(\\.[A-Za-z0-9_-]+)*\\.[a-z][A-Za-z0-9_-]*".r
    val zf           = new ZipFile(jar)
    val shaded =
      try
        zf.entries()
          .asScala
          .filter(_.getName.endsWith(".class"))
          .flatMap(ent => stringConstants(zf.getInputStream(ent).readAllBytes()))
          .collect { case s @ propertyLike(_*) => s }
          .toVector
          .distinct
          .sorted
      finally zf.close()
    for (s <- shaded)
      System.err.println(
        s"Shaded coursier system property: $s " +
          s"(add ${s.stripPrefix("coursierapi.shaded.")} to CoursierProperties.list if it is one)"
      )
    assert(shaded.isEmpty)
  }

}
