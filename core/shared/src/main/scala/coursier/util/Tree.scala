package coursier.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.annotation.tailrec

object Tree {

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], show: T => String): String = {

    val buffer = new StringBuilder
    val printLine: (String) => Unit = { line =>
      buffer.append(line).append('\n')
    }

    def last[E, O](seq: Seq[E])(f: E => O) =
      seq.takeRight(1).map(f)
    def init[E, O](seq: Seq[E])(f: E => O) =
      seq.dropRight(1).map(f)

    /*
     * Add elements to the stack
     * @param elems elements to add
     * @param isLast a list that contains whether an element is the last in its siblings or not.
     */
    def childrenWithLast(elems: Seq[T],
                         isLast: Seq[Boolean]): Seq[(T, Seq[Boolean])] = {

      val isNotLast = isLast :+ false

      init(elems)(_ -> isNotLast) ++
        last(elems)(_ -> (isLast :+ true))
    }

    /**
      * Has to end with a "─"
      */
    def showLine(isLast: Seq[Boolean]): String = {
      val initPrefix = init(isLast) {
        case true => "   "
        case false => "│  "
      }.mkString

      val lastPrefix = last(isLast) {
        case true => "└─ "
        case false => "├─ "
      }.mkString

      initPrefix + lastPrefix
    }

    // Depth-first traverse
    @tailrec
    def helper(stack: Seq[(T, Seq[Boolean])]): Unit = {
      stack match {
        case (elem, isLast) +: next =>
          printLine(showLine(isLast) + show(elem))
          helper(childrenWithLast(children(elem), isLast) ++ next)
        case Seq() =>
      }
    }



    def objectMapper = {
      val mapper = new ObjectMapper with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper
    }


    helper(childrenWithLast(roots, Vector[Boolean]()))

//    case class SomeData(i: Int, s: Map[String, SomeData])

    val depMap = Map()
//    val jsonString = objectMapper.writeValueAsString( map)
//    println(childrenWithLast(roots, Vector[Boolean]()))
// Depth-first traverse
    @tailrec
    def helperMap(stack: Seq[(T, Seq[Boolean])]): Unit = {
      stack match {
        case (elem, isLast) +: next =>
//          println(stack)
          println("elem:", elem)
          println("next:", next)
//          printLine(showLine(isLast) + show(elem))
          helperMap(childrenWithLast(children(elem), isLast) ++ next)
        case Seq() =>
      }
    }
    helperMap(childrenWithLast(roots, Vector[Boolean]()))

//    println(jsonString)

    buffer
      .dropRight(1) // drop last appended '\n'
      .toString
  }

}
