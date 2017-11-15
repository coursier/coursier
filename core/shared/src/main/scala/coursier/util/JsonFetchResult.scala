package coursier.util

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import coursier.Artifact
import coursier.core.Dependency

import scala.collection.mutable.ArrayBuffer


case class JsonPrintRequirement(fileByArtifact: collection.mutable.Map[String, File], depToArtifacts: Map[Dependency, ArrayBuffer[Artifact]])

object JsonFetchResult {

  def apply[T](roots: IndexedSeq[T])
              (children: T => Seq[T], reconciledVersionStr: T => String, originalVersionStr: T => String, getFiles: T => Seq[(String, String)]): String = {

    case class JsonNode(coord: String, files: Seq[(String, String)], dependencies: ArrayBuffer[JsonNode]) {
      def addChild(x: JsonNode): Unit = {
        dependencies.append(x)
      }
    }

    val conflictResolution = collection.mutable.Map[String, String]()

    def makeJson(elems: Seq[T], ancestors: Set[T], parentElem: JsonNode): Unit = {
      val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
      for (elem <- unseenElems) {
        val finalVersionStr = reconciledVersionStr(elem)
        val requestedVersionStr = originalVersionStr(elem)
        println(finalVersionStr, requestedVersionStr)

        if (!requestedVersionStr.equals(finalVersionStr)) {
          conflictResolution.put(requestedVersionStr, finalVersionStr)
        }

        val childNode = JsonNode(finalVersionStr, getFiles(elem), ArrayBuffer.empty)
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
    println(conflictResolution)
    objectMapper.writeValueAsString(root)
  }

}
