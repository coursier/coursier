package coursier.core

sealed abstract class Overrides extends Product with Serializable {
  def get(key: DependencyManagement.Key): Option[DependencyManagement.Values]
  def contains(key: DependencyManagement.Key): Boolean
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty

  def maps: Seq[DependencyManagement.GenericMap]
  def flatten: DependencyManagement.GenericMap =
    DependencyManagement.addAll(
      Map.empty[DependencyManagement.Key, DependencyManagement.Values],
      maps
    )

  def filter(f: (DependencyManagement.Key, DependencyManagement.Values) => Boolean): Overrides
  def map(
    f: (
      DependencyManagement.Key,
      DependencyManagement.Values
    ) => (DependencyManagement.Key, DependencyManagement.Values)
  ): Overrides
  def mapMap(
    f: DependencyManagement.GenericMap => Option[DependencyManagement.GenericMap]
  ): Overrides

  def hasProperties: Boolean
}

object Overrides {

  private final case class Impl(map: DependencyManagement.GenericMap) extends Overrides {

    override lazy val hashCode: Int = map.hashCode()

    def get(key: DependencyManagement.Key): Option[DependencyManagement.Values] =
      map.get(key)
    def contains(key: DependencyManagement.Key): Boolean =
      map.contains(key)
    def isEmpty: Boolean =
      map.forall(_._2.isEmpty)

    def maps: Seq[DependencyManagement.GenericMap] =
      Seq(map)
    def filter(f: (DependencyManagement.Key, DependencyManagement.Values) => Boolean): Overrides = {
      val updatedMap = map.filter {
        case (k, v) =>
          f(k, v)
      }
      if (map.size == updatedMap.size) this
      else Overrides(updatedMap)
    }
    def map(
      f: (
        DependencyManagement.Key,
        DependencyManagement.Values
      ) => (DependencyManagement.Key, DependencyManagement.Values)
    ): Overrides = {
      var changed = false
      val updatedMap = map.map {
        case kv @ (k, v) =>
          // FIXME Key collisions after applying f?
          val updated = f(k, v)
          if (!changed && kv != updated)
            changed = true
          updated
      }
      if (changed) Overrides(updatedMap)
      else this
    }
    lazy val hasProperties = map.exists { t =>
      t._1.organization.value.contains("$") ||
      t._1.name.value.contains("$") ||
      t._1.classifier.value.contains("$") ||
      t._1.`type`.value.contains("$") ||
      t._2.config.value.contains("$") ||
      t._2.version.contains("$") ||
      t._2.minimizedExclusions.hasProperties
    }
    def mapMap(
      f: DependencyManagement.GenericMap => Option[DependencyManagement.GenericMap]
    ): Overrides =
      f(map)
        .map(Overrides(_))
        .getOrElse(this)
  }

  private val empty0 = Impl(Map.empty)
  def empty: Overrides =
    empty0

  def apply(map: DependencyManagement.GenericMap): Overrides =
    if (map.forall(_._2.isEmpty)) empty
    else Impl(map.filter(!_._2.isEmpty))

  def add(overrides: Overrides*): Overrides =
    overrides.filter(_.nonEmpty) match {
      case Seq()     => empty
      case Seq(elem) => elem
      case more =>
        Impl(
          DependencyManagement.addAll(
            Map.empty[DependencyManagement.Key, DependencyManagement.Values],
            more.flatMap(_.maps)
          )
        )
    }
}
