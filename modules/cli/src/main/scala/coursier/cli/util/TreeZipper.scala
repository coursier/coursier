package coursier.cli.util

import cats.Eq
import cats.data.{Chain, NonEmptyChain}
import cats.syntax.eq._

/** Implementation of a Tree Zipper that walks across the branches skipping potential cycles
  *
  * This zipper has been specifically implemented for being used in the generation of the JsonReport
  *
  * @param path
  *   Path traversed so far to the current item. The head of path is the current focus of the zipper
  * @param fetchChildren
  *   Function used to fetch the children of the current focus
  */
final class TreeZipper[A: Eq](
  path: NonEmptyChain[A],
  fetchChildren: A => Seq[A]
) {

  def focus: A = path.head

  private def parents: Chain[A] = path.tail

  private val visited = path.iterator.toSet

  /** Attempts to walk the tree down to the first of the children.
    *
    * This operation will return None in the following cases:
    *   - Current item has no children
    *   - Current item has one children which has already been visited before
    *
    * In another case, this operation will return a new zipper focused in the first child
    */
  def moveDown: Option[TreeZipper[A]] = {
    val nonVisitedChildren = fetchChildren(focus).filterNot(visited)
    if (nonVisitedChildren.isEmpty) None
    else {
      val newPath = nonVisitedChildren.head +: path
      Some(new TreeZipper(newPath, fetchChildren))
    }
  }

  /** Attempts to move the zipper up in the walked path, in case this is not the root
    */
  def moveUp: Option[TreeZipper[A]] =
    parents.uncons.map { case (item, superPath) =>
      val parentPath = NonEmptyChain.fromChainPrepend(item, superPath)
      new TreeZipper(parentPath, fetchChildren)
    }

  /** This operation will return a chain of zippers pointing to each of the siblings of the current
    * focus.
    *
    * If any of the siblings is an item that has already been visited (it's present in the path)
    * then it will be skipped
    */
  def siblings: Chain[TreeZipper[A]] =
    Chain.fromOption(parents.headOption).flatMap { parentItem =>
      val seq = fetchChildren(parentItem)
        .view
        .filterNot(visited)
        .map { item =>
          val newPath = NonEmptyChain.fromChainPrepend(item, parents)
          new TreeZipper(newPath, fetchChildren)
        }
        .toSeq

      Chain.fromSeq(seq)
    }

  /** This operation returns a chain of zippers pointing to each of the children of the current
    * focus.
    *
    * In the present of any cycles (any children poiting to an item already present in the path),
    * then it will be skipped in the returned chain.
    */
  def children: Chain[TreeZipper[A]] =
    Chain.fromOption(moveDown).flatMap(child => Chain.one(child) ++ child.siblings)

}
object TreeZipper {

  /** Instantiates a new zipper at the given root
    *
    * @param root
    *   Root to be used for the zipper
    * @param fetchChildren
    *   Operation that will return the children of any given item
    * @return
    *   A new zipper focused at the given root
    */
  def of[A: Eq](root: A, fetchChildren: A => Seq[A]): TreeZipper[A] =
    new TreeZipper[A](NonEmptyChain.one(root), fetchChildren)
}
