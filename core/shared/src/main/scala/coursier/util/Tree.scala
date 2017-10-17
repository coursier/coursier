package coursier.util

import scala.collection.mutable.ArrayBuffer

object Tree {

  def apply[T](roots: IndexedSeq[T])(children: T => Seq[T], show: T => String): String = {


    def recursivePrint(roots: Seq[T], ancestors: Set[T], prefix: String, acc: String => Unit): Unit = {
      for (root <- roots) {
        if (ancestors.contains(root)) {
          return
        }
        val isLast = roots.indexOf(root) == roots.length - 1
        val tee = if (isLast) "└─ " else "├─ "
        acc(prefix + tee + show(root))

        val extraPrefix = if (isLast) "   " else "|  "
        recursivePrint(children(root), ancestors + root, prefix + extraPrefix, acc)
      }
    }

    val b = new ArrayBuffer[String]
    recursivePrint(roots, Set(), "", b += _)
    b.mkString("\n")
  }

}
