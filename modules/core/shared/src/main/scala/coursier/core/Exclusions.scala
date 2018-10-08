package coursier.core

object Exclusions {

  def partition(exclusions: Set[(Organization, String)]): (Boolean, Set[Organization], Set[String], Set[(Organization, String)]) = {

    val (wildCards, remaining) = exclusions
      .partition{case (org, name) => org == allOrganizations || name == "*" }

    val all = wildCards
      .contains(one.head)

    val excludeByOrg = wildCards
      .collect{case (org, "*") if org != allOrganizations => org }
    val excludeByName = wildCards
      .collect{case (allOrganizations, name) if name != "*" => name }

    (all, excludeByOrg, excludeByName, remaining)
  }

  def apply(exclusions: Set[(Organization, String)]): (Organization, String) => Boolean = {

    val (all, excludeByOrg, excludeByName, remaining) = partition(exclusions)

    if (all) (_, _) => false
    else
      (org, name) => {
        !excludeByName(name) &&
        !excludeByOrg(org) &&
        !remaining((org, name))
      }
  }

  def minimize(exclusions: Set[(Organization, String)]): Set[(Organization, String)] = {

    val (all, excludeByOrg, excludeByName, remaining) = partition(exclusions)

    if (all) one
    else {
      val filteredRemaining = remaining
        .filter{case (org, name) =>
          !excludeByOrg(org) &&
          !excludeByName(name)
        }

      excludeByOrg.map((_, "*")) ++
        excludeByName.map((allOrganizations, _)) ++
        filteredRemaining
    }
  }

  val allOrganizations = Organization("*")

  val zero = Set.empty[(Organization, String)]
  val one = Set((allOrganizations, "*"))

  def join(x: Set[(Organization, String)], y: Set[(Organization, String)]): Set[(Organization, String)] =
    minimize(x ++ y)

  def meet(x: Set[(Organization, String)], y: Set[(Organization, String)]): Set[(Organization, String)] = {

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

      excludeByOrg.map((_, "*")) ++
        excludeByName.map((allOrganizations, _)) ++
        remaining
    }
  }

}
