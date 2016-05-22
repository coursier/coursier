import java.io.{ File, FileOutputStream }

import scalaz.stream._
import scalaz.concurrent.Task

object Main extends App {

  val pch = Process.constant((i:Int) => Task.now(())).take(3)
  val count = Process.constant(1).toSource.to(pch).runLog.run.size
  assert(count == 3)

  val fos = new FileOutputStream(new File("output"))
  fos.write("OK".getBytes("UTF-8"))
  fos.close()
}
