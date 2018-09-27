import java.io.File
import java.nio.file.Files

import scala.collection.JavaConverters._

object Main extends App {

  val cp = new collection.mutable.ArrayBuffer[File]

  def buildCp(loader: ClassLoader): Unit =
    if (loader != null) {
      loader match {
        case u: java.net.URLClassLoader =>
          cp ++= u.getURLs
            .map(_.toURI)
            .map(new File(_))
        case _ =>
      }

      buildCp(loader.getParent)
    }

  buildCp(Thread.currentThread().getContextClassLoader)

  val sbtBase = new File(sys.props.getOrElse(
    "sbt.global.base",
    sys.props("user.home") + "/.sbt"
  ))
  val prefix = new File(sbtBase, "boot").getAbsolutePath

  def fromBootAndUnique(name: String): Unit = {
    val jars = cp.filter(_.getName.startsWith(name)).distinct
    assert(jars.length == 1, s"Found 0 or multiple JARs for $name: $jars")

    val Seq(jar) = jars

    assert(jar.getAbsolutePath.startsWith(prefix), s"JAR for $name ($jar) not under $prefix")
  }

  val props = Thread.currentThread()
    .getContextClassLoader
    .getResources("library.properties")
    .asScala
    .toVector
    .map(_.toString)
    .sorted

  // That one doesn't pass with sbt 1.x, maybe because of classloader filtering?
  // fromBootAndUnique("scala-library")
  assert(props.lengthCompare(1) == 0, s"Found several library.properties files in classpath: $props")

  fromBootAndUnique("scala-reflect")
  fromBootAndUnique("scala-compiler")

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
