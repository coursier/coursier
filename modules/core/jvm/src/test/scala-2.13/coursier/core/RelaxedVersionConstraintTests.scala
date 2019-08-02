package coursier.core

import cats.kernel.Monoid
import cats.kernel.laws.discipline.MonoidTests
import cats.tests.CatsSuite
import coursier.core.VersionConstraintInstances._

class RelaxedVersionConstraintTests extends CatsSuite {

  implicit val monoid: Monoid[VersionConstraint] =
    new Monoid[VersionConstraint] {
      def empty = VersionConstraint.all
      def combine(x: VersionConstraint, y: VersionConstraint) =
        VersionConstraint.relaxedMerge(x, y)
    }

  checkAll("RelaxedVersionConstraint.MonoidLaws", MonoidTests[VersionConstraint].monoid)

}
