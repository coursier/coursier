package coursier.core

object Exclusions {

  def partition(exclusions: Set[(Organization, ModuleName)]): (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)]) = {

    val (wildCards, remaining) = exclusions
      .partition{case (org, name) => org == allOrganizations || name == allNames }

    val all = wildCards
      .contains(one.head)

    val excludeByOrg = wildCards
      .collect{case (org, `allNames`) if org != allOrganizations => org }
    val excludeByName = wildCards
      .collect{case (`allOrganizations`, name) if name != allNames => name }

    (all, excludeByOrg, excludeByName, remaining)
  }

  def apply(exclusions: Set[(Organization, ModuleName)]): (Organization, ModuleName) => Boolean = {

    val (all, excludeByOrg, excludeByName, remaining) = partition(exclusions)

    if (all) (_, _) => false
    else
      (org, name) => {
        !excludeByName(name) &&
        !excludeByOrg(org) &&
        !remaining((org, name))
      }
  }

  def minimize(exclusions: Set[(Organization, ModuleName)]): Set[(Organization, ModuleName)] = {

    val (all, excludeByOrg, excludeByName, remaining) = partition(exclusions)

    if (all) one
    else {
      val filteredRemaining = remaining
        .filter{case (org, name) =>
          !excludeByOrg(org) &&
          !excludeByName(name)
        }

      excludeByOrg.map((_, allNames)) ++
        excludeByName.map((allOrganizations, _)) ++
        filteredRemaining
    }
  }

  val allOrganizations = Organization("*")
  val allNames = ModuleName("*")

  val zero = Set.empty[(Organization, ModuleName)]
  val one = Set((allOrganizations, allNames))

  def join(x: Set[(Organization, ModuleName)], y: Set[(Organization, ModuleName)]): Set[(Organization, ModuleName)] =
    minimize(x ++ y)

  def meet(x: Set[(Organization, ModuleName)], y: Set[(Organization, ModuleName)]): Set[(Organization, ModuleName)] = {

    val ((xAll, xExcludeByOrg, xExcludeByName, xRemaining), (yAll, yExcludeByOrg, yExcludeByName, yRemaining)) =
      (partition(x), partition(y))

    val all = xAll && yAll

    if (all) one
    else {
      val excludeByOrg =
        if (xAll) yExcludeByOrg
        else if (yAll) xExcludeByOrg
        else xExcludeByOrg intersect yExcludeByOrg
      val excludeByName =
        if (xAll) yExcludeByName
        else if (yAll) xExcludeByName
        else xExcludeByName intersect yExcludeByName

      val remaining =
        xRemaining.filter{case (org, name) => yAll || yExcludeByOrg(org) || yExcludeByName(name)} ++
        yRemaining.filter{case (org, name) => xAll || xExcludeByOrg(org) || xExcludeByName(name)} ++
          (xRemaining intersect yRemaining)

      excludeByOrg.map((_, allNames)) ++
        excludeByName.map((allOrganizations, _)) ++
        remaining
    }
  }

}
