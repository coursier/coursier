package coursier.core

import cats.kernel.Eq
import org.scalacheck.util.Buildable
import org.scalacheck.{Arbitrary, Gen}

object VersionConstraintInstances {

  implicit val versionArbitrary: Arbitrary[Version] = ???

  implicit val versionConstraintEq = Eq.fromUniversalEquals[VersionConstraint]

  implicit val versionConstraintArbitrary: Arbitrary[VersionConstraint] = {

    val specificVersions =
      for {
        singleVer <- Gen.oneOf(true, false)
        first <- versionArbitrary.arbitrary
        versions <- {
          if (singleVer)
            Gen.const(Seq(first))
          else
            for {
              count <- Gen.oneOf(1, 4)
              extra <- Gen.sequence(Seq.fill(count)(versionArbitrary.arbitrary))(Buildable.buildableFactory[Version, Seq[Version]])
            } yield extra
        }
      } yield versions

    Arbitrary {
      for {
        kind <- Gen.choose(0, 2)
        res <- {
          if (kind == 0)
            specificVersions.map(VersionConstraint(VersionInterval.zero, _))
          else
            ???
        }
      } yield res
    }
  }

}
