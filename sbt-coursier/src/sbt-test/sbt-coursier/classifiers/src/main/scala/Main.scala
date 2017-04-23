import java.io.File
import java.nio.file.Files

import scala.util.Try

object Main extends App {

  def classFound(clsName: String) = Try(
    Thread.currentThread()
      .getContextClassLoader()
      .loadClass(clsName)
  ).toOption.nonEmpty

  val classifierTest = classFound("org.jclouds.openstack.nova.compute.NovaComputeServiceLiveTest")
  val noClassifier = classFound("org.jclouds.openstack.nova.compute.config.NovaComputeServiceContextModule")

  assert(classifierTest)
  assert(noClassifier)


  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
