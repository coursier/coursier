package coursier.internal

import coursier.core.{ModuleName, Organization}
import coursier.{Dependency, Module}

object Typelevel {

  val mainLineOrg = Organization("org.scala-lang")
  val typelevelOrg = Organization("org.typelevel")

  val modules = Set(
    ModuleName("scala-compiler"),
    ModuleName("scala-library"),
    ModuleName("scala-library-all"),
    ModuleName("scala-reflect"),
    ModuleName("scalap")
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