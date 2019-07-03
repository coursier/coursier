package coursier.core

object Exclusions {

  def partition(exclusions: Set[(Organization, ModuleName)]): (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)]) = {

    var all0 = false
    val excludeByOrg0 = Set.newBuilder[Organization]
    val excludeByName0 = Set.newBuilder[ModuleName]
    val remaining0 = Set.newBuilder[(Organization, ModuleName)]

    val it = exclusions.iterator
    while (it.hasNext) {
      val excl = it.next()
      if (excl._1 == allOrganizations) {
        if (excl._2 == allNames)
          all0 = true
        else
          excludeByName0 += excl._2
      } else if (excl._2 == allNames)
        excludeByOrg0 += excl._1
      else
        remaining0 += excl
    }

    (all0, excludeByOrg0.result(), excludeByName0.result(), remaining0.result())
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

      val b = Set.newBuilder[(Organization, ModuleName)]
      b.sizeHint(excludeByOrg.size + excludeByName.size + remaining.size)

      val orgIt = excludeByOrg.iterator
      while (orgIt.hasNext)
        b += ((orgIt.next(), allNames))

      val nameIt = excludeByName.iterator
      while (nameIt.hasNext)
        b += ((allOrganizations, nameIt.next()))

      val remIt = remaining.iterator
      while (remIt.hasNext) {
        val elem = remIt.next()
        if (!excludeByOrg(elem._1) && !excludeByName(elem._2))
          b += elem
      }

      b.result()
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
        xRemaining.filter { case e @ (org, name) => yAll || yExcludeByOrg(org) || yExcludeByName(name) || yRemaining(e) } ++
          yRemaining.filter { case e @ (org, name) => xAll || xExcludeByOrg(org) || xExcludeByName(name) || xRemaining(e) }

      excludeByOrg.map((_, allNames)) ++
        excludeByName.map((allOrganizations, _)) ++
        remaining
    }
  }

}
