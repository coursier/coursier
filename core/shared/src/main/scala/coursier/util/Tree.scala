package coursier.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.collection.mutable.ArrayBuffer

object Tree {

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
//        println(show(elem))
        val childNode = JsonNode(show(elem), getFiles(elem), ArrayBuffer.empty)
        parentElem.addChild(childNode)
//        println(getFiles(elem))
        makeJson(children(elem), ancestors + elem, childNode)
      }
    }

    def objectMapper = {
      val mapper = new ObjectMapper with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper
    }


    //    var result: mutable.Map[Any, Any] = Map[Any, Any]()
    //    makeJson(roots, Set(), "", result)
    //    val jsonString = objectMapper.writeValueAsString(result)
    //    println(jsonString)
    val root = JsonNode("root", Seq(), ArrayBuffer.empty)
    println("starting json writing...")
//    for (r <- roots) {
//      println(r.len)
//    }
    makeJson(roots, Set(), root)
    println("finishing making json object")
    //    val node = JsonNode("123", JsonNode("456"))
    objectMapper.writeValueAsString(root)
//    val b = new ArrayBuffer[String]
//    recursivePrint(roots, Set(), "", b += _)
//    b.mkString("\n")


  }

}
