import java.io.{ File, FileOutputStream }

import scala.util.Try

object Main extends App {

  def classFound(clsName: String) = Try(
    Thread.currentThread()
      .getContextClassLoader()
      .loadClass(clsName)
  ).toOption.nonEmpty

  val shapelessFound = classFound("shapeless.HList")
  val argonautFound = classFound("argonaut.Json")
  val argonautShapelessFound = classFound("argonaut.derive.MkEncodeJson")

  assert(argonautShapelessFound)
  assert(!shapelessFound)
  assert(!argonautFound)

  val fos = new FileOutputStream(new File("output"))
  fos.write("OK".getBytes("UTF-8"))
  fos.close()
}
