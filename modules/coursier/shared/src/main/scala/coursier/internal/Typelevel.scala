package coursier.extra

import coursier.{Dependency, Module, moduleNameString, organizationString}

object Typelevel {

  val mainLineOrg = org"org.scala-lang"
  val typelevelOrg = org"org.typelevel"

  val modules = Set(
    name"scala-compiler",
    name"scala-library",
    name"scala-library-all",
    name"scala-reflect",
    name"scalap"
    // any other?
  )

  def swap(module: Module): Module =
    if (module.organization == mainLineOrg && modules(module.name) && module.attributes.isEmpty)
      module.copy(
        organization = typelevelOrg
      )
    else
      module

  val swap: Dependency => Dependency =
    dependency => dependency.copy(
      module = swap(dependency.module)
    )

}