package coursier.cli.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.collection.mutable.ArrayBuffer


object JsonReport {

  def apply[T](roots: IndexedSeq[T], conflictResolutionForRoots: Map[String, String])
              (children: T => Seq[T], reconciledVersionStr: T => String, requestedVersionStr: T => String, getFiles: T => Seq[(String, String)]): String = {

    case class JsonNode(coord: String, files: Seq[(String, String)], dependencies: ArrayBuffer[JsonNode]) {
      def addChild(x: JsonNode): Unit = {
        dependencies.append(x)
      }
    }


    def makeJson(elems: Seq[T], ancestors: Set[T], parentElem: JsonNode): Unit = {
      val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
      for (elem <- unseenElems) {
        val childNode = JsonNode(reconciledVersionStr(elem), getFiles(elem), ArrayBuffer.empty)
        parentElem.addChild(childNode)
        makeJson(children(elem), ancestors + elem, childNode)
      }
    }

    def objectMapper = {
      val mapper = new ObjectMapper with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper
    }

    val root = JsonNode("root", Seq(), ArrayBuffer.empty)
    makeJson(roots, Set(), root)

    case class Report(conflict_resolution: Map[String, String], dependencies: Seq[JsonNode])
    objectMapper.writeValueAsString(Report(conflictResolutionForRoots, root.dependencies))
  }

}
