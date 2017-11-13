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

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], show: T => String, getFiles: T => Seq[String]): String = {

    /**
      * Recursively go down the resolution for the elems to construct the tree for print out.
      *
      * @param elems     Seq of Elems that have been resolved
      * @param ancestors a set of Elems to keep track for cycle detection
      * @param prefix    prefix for the print out
      * @param acc       accumulation method on a string
      */
    def recursivePrint(elems: Seq[T], ancestors: Set[T], prefix: String, acc: String => Unit): Unit = {
      val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
      val unseenElemsLen = unseenElems.length
      for ((elem, idx) <- unseenElems.iterator.zipWithIndex) {
        val isLast = idx == unseenElemsLen - 1
        val tee = if (isLast) "└─ " else "├─ "
        acc(prefix + tee + show(elem))

        val extraPrefix = if (isLast) "   " else "│  "
        recursivePrint(children(elem), ancestors + elem, prefix + extraPrefix, acc)
      }
    }

    case class JsonNode(coord: String, files: Seq[String], dependencies: ArrayBuffer[JsonNode]) {
      def addChild(x: JsonNode): Unit = {
        dependencies.append(x)
      }
    }

    def makeJson(elems: Seq[T], ancestors: Set[T], parentElem: JsonNode): Unit = {
      val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
      for (elem <- unseenElems) {
        val childNode = JsonNode(show(elem), getFiles(elem), ArrayBuffer.empty)
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
    //    println("jsoning...")
    makeJson(roots, Set(), root)
    //    println("ending...")
    objectMapper.writeValueAsString(root)
  }

}
