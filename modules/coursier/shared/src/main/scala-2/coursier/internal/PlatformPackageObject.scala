package coursier.internal

import coursier.util.StringInterpolators._

import scala.language.implicitConversions

/** Scala 2 members of the `coursier` package object: the string interpolators, as implicit
  * conversions of `StringContext`, for binary compatibility with earlier versions.
  */
trait PlatformPackageObject {

  implicit def organizationString(sc: StringContext): SafeOrganization =
    SafeOrganization(sc)
  implicit def moduleNameString(sc: StringContext): SafeModuleName =
    SafeModuleName(sc)
  implicit def moduleString(sc: StringContext): SafeModule =
    SafeModule(sc)
  implicit def moduleExclString(sc: StringContext): SafeModuleExclusionMatcher =
    SafeModuleExclusionMatcher(sc)
  implicit def moduleInclString(sc: StringContext): SafeModuleInclusionMatcher =
    SafeModuleInclusionMatcher(sc)
  implicit def dependencyString(sc: StringContext): SafeDependency =
    SafeDependency(sc)
  implicit def mavenRepositoryString(sc: StringContext): SafeMavenRepository =
    SafeMavenRepository(sc)
  implicit def ivyRepositoryString(sc: StringContext): SafeIvyRepository =
    SafeIvyRepository(sc)

}
