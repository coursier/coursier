package coursier.core

import cats.kernel.Monoid
import cats.kernel.laws.discipline.MonoidTests
import cats.tests.CatsSuite
import coursier.core.VersionConstraintInstances._

import scala.collection.mutable

class VersionConstraintTests extends CatsSuite {

  implicit val monoid: Monoid[Option[VersionConstraint]] =
    new Monoid[Option[VersionConstraint]] {
      def empty = Some(VersionConstraint.all)
      def combine(xOpt: Option[VersionConstraint], yOpt: Option[VersionConstraint]) =
        for (x <- xOpt; y <- yOpt; res <- VersionConstraint.merge(x, y)) yield res

      override def combineAll(as: IterableOnce[Option[VersionConstraint]]) = {
        val b = mutable.ArrayBuilder.make[VersionConstraint]
        val it = as.iterator
        var empty = false
        while (!empty && it.hasNext)
          it.next() match {
            case Some(c) =>
              b += c
            case None =>
              empty = true
          }

        if (empty)
          None
        else
          VersionConstraint.merge(b.result().toSeq: _*)
      }
    }

  checkAll("VersionConstraint.MonoidLaws", MonoidTests[Option[VersionConstraint]].monoid)

}
