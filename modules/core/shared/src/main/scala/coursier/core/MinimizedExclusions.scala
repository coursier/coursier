package coursier.core

import dataclass.data
import coursier.core.Exclusions.{allOrganizations, allNames}
import MinimizedExclusions._

/** This file defines a special-purpose structure for exclusions that has the following
  * properties/goals:
  *   - The exclusion data is always minimized (minimized meaning overlapping rules are removed)
  *   - The data structure is split into various cases, optimizing common cases for join/meet
  *   - The hashcode is cached, such that recalculating the hashcode for these exclusions is cached.
  */
private[coursier] object MinimizedExclusions {

  private[coursier] val zero = MinimizedExclusions(ExcludeNone)
  private[coursier] val one  = MinimizedExclusions(ExcludeAll)

  private[coursier] sealed trait ExclusionData {
    def apply(org: Organization, module: ModuleName): Boolean

    def join(other: ExclusionData): ExclusionData
    def meet(other: ExclusionData): ExclusionData

    def partitioned()
      : (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)])
    def map(f: String => String): ExclusionData

    def size(): Int

    def subsetOf(other: ExclusionData): Boolean

    def toSet(): Set[(Organization, ModuleName)]
  }

  private[coursier] case object ExcludeNone extends ExclusionData {
    override def apply(org: Organization, module: ModuleName): Boolean = true

    override def join(other: ExclusionData): ExclusionData = other
    override def meet(other: ExclusionData): ExclusionData = ExcludeNone
    override def partitioned()
      : (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)]) =
      (false, Set.empty, Set.empty, Set.empty)
    override def map(f: String => String): ExclusionData = ExcludeNone

    override def size(): Int                              = 0
    override def subsetOf(other: ExclusionData): Boolean  = true
    override def toSet(): Set[(Organization, ModuleName)] = Set.empty
  }

  private[coursier] case object ExcludeAll extends ExclusionData {
    override def apply(org: Organization, module: ModuleName): Boolean = false

    override def join(other: ExclusionData): ExclusionData = ExcludeAll
    override def meet(other: ExclusionData): ExclusionData = other

    override def partitioned()
      : (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)]) =
      (true, Set.empty, Set.empty, Set.empty)
    override def map(f: String => String): ExclusionData = ExcludeAll

    override def size(): Int                              = 1
    override def subsetOf(other: ExclusionData): Boolean  = other == ExcludeAll
    override def toSet(): Set[(Organization, ModuleName)] = Set((allOrganizations, allNames))
  }

  private[coursier] case class ExcludeSpecific(
    byOrg: Set[Organization],
    byModule: Set[ModuleName],
    specific: Set[(Organization, ModuleName)]
  ) extends ExclusionData {
    override def apply(org: Organization, module: ModuleName): Boolean =
      !byModule(module) &&
      !byOrg(org) &&
      !specific((org, module))

    override def join(other: ExclusionData): ExclusionData =
      other match {
        case ExcludeNone => this
        case ExcludeAll  => ExcludeAll
        case ExcludeSpecific(otherByOrg, otherByModule, otherSpecific) =>
          val joinedByOrg    = byOrg ++ otherByOrg
          val joinedByModule = byModule ++ otherByModule

          val joinedSpecific =
            specific.filter { case e @ (org, module) =>
              !otherByOrg(org) && !otherByModule(module)
            } ++
              otherSpecific.filter { case e @ (org, module) =>
                !byOrg(org) && !byModule(module)
              }

          ExcludeSpecific(joinedByOrg, joinedByModule, joinedSpecific)
      }

    override def meet(other: ExclusionData): ExclusionData =
      other match {
        case ExcludeNone => this
        case ExcludeAll  => ExcludeAll
        case ExcludeSpecific(otherByOrg, otherByModule, otherSpecific) =>
          val metByOrg    = byOrg intersect otherByOrg
          val metByModule = byModule intersect otherByModule

          val metSpecific =
            specific.filter { case e @ (org, module) =>
              otherByOrg(org) || otherByModule(module) || otherSpecific(e)
            } ++
              otherSpecific.filter { case e @ (org, module) =>
                byOrg(org) || byModule(module) || specific(e)
              }

          if (metByOrg.isEmpty && metByModule.isEmpty && metSpecific.isEmpty)
            ExcludeNone
          else
            ExcludeSpecific(metByOrg, metByModule, metSpecific)
      }

    override def partitioned()
      : (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)]) =
      (false, byOrg, byModule, specific)

    override def map(f: String => String): ExclusionData =
      ExcludeSpecific(
        byOrg.map(_.map(f)),
        byModule.map(_.map(f)),
        specific.map { case (org, module) =>
          org.map(f) -> module.map(f)
        }
      )

    override def size(): Int = byOrg.size + byModule.size + specific.size

    override def subsetOf(other: ExclusionData): Boolean =
      other match {
        case ExcludeNone => false
        case ExcludeAll  => false
        case ExcludeSpecific(otherByOrg, otherByModule, otherSpecific) =>
          byOrg.subsetOf(otherByOrg) && byModule.subsetOf(otherByModule) && specific.subsetOf(
            otherSpecific
          )
      }

    override def toSet(): Set[(Organization, ModuleName)] =
      byOrg.map(_ -> allNames) ++ byModule.map(allOrganizations -> _) ++ specific
  }

  private[coursier] def apply(exclusions: Set[(Organization, ModuleName)]): MinimizedExclusions = {
    if (exclusions.isEmpty)
      return zero

    val excludeByOrg0  = Set.newBuilder[Organization]
    val excludeByName0 = Set.newBuilder[ModuleName]
    val remaining0     = Set.newBuilder[(Organization, ModuleName)]

    val it = exclusions.iterator
    while (it.hasNext) {
      val excl = it.next()
      if (excl._1 == allOrganizations)
        if (excl._2 == allNames)
          return one
        else
          excludeByName0 += excl._2
      else if (excl._2 == allNames)
        excludeByOrg0 += excl._1
      else
        remaining0 += excl
    }

    MinimizedExclusions(ExcludeSpecific(
      excludeByOrg0.result(),
      excludeByName0.result(),
      remaining0.result()
    ))
  }
}

private[coursier] case class MinimizedExclusions(data: ExclusionData) {
  def apply(org: Organization, module: ModuleName): Boolean = data(org, module)

  def join(other: MinimizedExclusions): MinimizedExclusions = {
    val newData = data.join(other.data)
    // If no data was changed, no need to construct a new instance and create a new hashcode
    if (newData eq this.data)
      return this
    else if (newData eq other.data)
      return other
    else
      return MinimizedExclusions(newData)
  }

  def meet(other: MinimizedExclusions): MinimizedExclusions = {
    val newData = data.meet(other.data)
    // If no data was changed, no need to construct a new instance and create a new hashcode
    if (newData eq this.data)
      return this
    else if (newData eq other.data)
      return other
    else
      return MinimizedExclusions(newData)
  }

  def map(f: String => String): MinimizedExclusions = {
    val newData = data.map(f)
    // If no data was changed, no need to construct a new instance and create a new hashcode
    if (newData eq this.data)
      return this
    else
      return MinimizedExclusions(newData)
  }

  def partitioned()
    : (Boolean, Set[Organization], Set[ModuleName], Set[(Organization, ModuleName)]) =
    data.partitioned()

  def isEmpty: Boolean = data == ExcludeNone

  def nonEmpty: Boolean = data != ExcludeNone

  def size(): Int = data.size()

  def subsetOf(other: MinimizedExclusions): Boolean = data.subsetOf(other.data)

  def toSet(): Set[(Organization, ModuleName)] = data.toSet()

  def toVector(): Vector[(Organization, ModuleName)] = data.toSet().toVector

  def toSeq(): Seq[(Organization, ModuleName)] = data.toSet().toSeq

  final override lazy val hashCode = data.hashCode()
}
