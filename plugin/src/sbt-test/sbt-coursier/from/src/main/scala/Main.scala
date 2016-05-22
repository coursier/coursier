import java.io.{ File, FileOutputStream }

import shapeless._

object Main extends App {
  case class CC(s: String)
  val cc = CC("OK")
  val l = Generic[CC].to(cc)
  val msg = l.head

  val fos = new FileOutputStream(new File("output"))
  fos.write(msg.getBytes("UTF-8"))
  fos.close()
}
