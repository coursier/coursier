import java.io.{ File, FileOutputStream }

object Main extends App {
  val fos = new FileOutputStream(new File("output"))
  fos.write("OK".getBytes("UTF-8"))
  fos.close()
}
