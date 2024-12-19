package coursier.core

import java.util.concurrent.ConcurrentHashMap

sealed abstract class Overrides extends Product with Serializable {
  def get(key: DependencyManagement.Key): Option[DependencyManagement.Values]
  def contains(key: DependencyManagement.Key): Boolean
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty

  def maps: Seq[DependencyManagement.Map]
  def flatten: DependencyManagement.Map =
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
  def mapMap(f: DependencyManagement.Map => Option[DependencyManagement.Map]): Overrides
}

object Overrides {

  private case object Empty extends Overrides {
    def get(key: DependencyManagement.Key): Option[DependencyManagement.Values] =
      None
    def contains(key: DependencyManagement.Key): Boolean =
      false
    def isEmpty: Boolean =
      true

    def maps: Seq[DependencyManagement.Map] =
      Nil
    def filter(f: (DependencyManagement.Key, DependencyManagement.Values) => Boolean): Overrides =
      this
    def map(
      f: (
        DependencyManagement.Key,
        DependencyManagement.Values
      ) => (DependencyManagement.Key, DependencyManagement.Values)
    ): Overrides =
      this
    def mapMap(f: DependencyManagement.Map => Option[DependencyManagement.Map]): Overrides =
      this
  }

  private final case class One(map: DependencyManagement.Map) extends Overrides {

    override lazy val hashCode: Int = map.hashCode()

    def get(key: DependencyManagement.Key): Option[DependencyManagement.Values] =
      map.get(key)
    def contains(key: DependencyManagement.Key): Boolean =
      map.contains(key)
    def isEmpty: Boolean =
      map.forall(_._2.isEmpty)

    def maps: Seq[DependencyManagement.Map] =
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
    private lazy val hasProperties = map.exists { t =>
      t._1.organization.value.contains("$") ||
      t._1.name.value.contains("$") ||
      t._1.classifier.value.contains("$") ||
      t._1.`type`.value.contains("$") ||
      t._2.config.value.contains("$") ||
      t._2.version.contains("$") ||
      t._2.minimizedExclusions.hasProperties
    }
    def mapMap(f: DependencyManagement.Map => Option[DependencyManagement.Map]): Overrides =
      if (hasProperties)
        f(map)
          .map(Overrides(_))
          .getOrElse(this)
      else
        this
  }

  private final case class Compose(values: Seq[Overrides]) extends Overrides {

    override lazy val hashCode: Int = values.hashCode()

    private val cache = new ConcurrentHashMap[DependencyManagement.Key, DependencyManagement.Values]

    private def compute(key: DependencyManagement.Key): DependencyManagement.Values = {
      val toCompose = values.flatMap(_.get(key))
      assert(toCompose.nonEmpty)
      if (toCompose.length == 1) toCompose.head
      else {
        var values                  = toCompose.head
        var versionUpdate           = ""
        var configUpdate            = Configuration.empty
        var reversedExtraExclusions = List.empty[MinimizedExclusions]
        var setOptional             = false
        for (otherValues <- toCompose.iterator.drop(1)) {
          if (values.version.isEmpty && versionUpdate.isEmpty && otherValues.version.nonEmpty)
            versionUpdate = otherValues.version
          if (values.config.isEmpty && configUpdate.isEmpty && otherValues.config.nonEmpty)
            configUpdate = otherValues.config
          if (otherValues.minimizedExclusions.nonEmpty)
            reversedExtraExclusions = otherValues.minimizedExclusions :: reversedExtraExclusions
          if (!setOptional && otherValues.optional)
            setOptional = true
        }
        if (versionUpdate.nonEmpty)
          values = values.withVersion(versionUpdate)
        if (configUpdate.nonEmpty)
          values = values.withConfig(configUpdate)
        if (reversedExtraExclusions.nonEmpty)
          values = values.withMinimizedExclusions(
            // no need to reverse reversedExtraExclusions,
            // order shouldn't matter for join
            reversedExtraExclusions.foldLeft(values.minimizedExclusions)(_.join(_))
          )
        if (setOptional && !values.optional)
          values = values.withOptional(setOptional)

        values
      }
    }

    def get(key: DependencyManagement.Key): Option[DependencyManagement.Values] = {
      val cached = cache.get(key)
      if (cached == null)
        if (values.exists(_.get(key).nonEmpty)) {
          val computed = compute(key)
          val parallel = cache.putIfAbsent(key, computed)
          Some(if (parallel == null) computed else parallel)
        }
        else
          None
      else
        Some(cached)
    }

    def contains(key: DependencyManagement.Key): Boolean =
      cache.contains(key) ||
      values.exists(_.get(key).nonEmpty)

    def isEmpty: Boolean =
      values.forall(_.isEmpty)

    def maps: Seq[DependencyManagement.Map] =
      values.flatMap(_.maps)
    def filter(f: (DependencyManagement.Key, DependencyManagement.Values) => Boolean): Overrides =
      add(values.map(_.filter(f)): _*)
    def map(
      f: (
        DependencyManagement.Key,
        DependencyManagement.Values
      ) => (DependencyManagement.Key, DependencyManagement.Values)
    ): Overrides =
      add(values.map(_.map(f)): _*)
    def mapMap(f: DependencyManagement.Map => Option[DependencyManagement.Map]): Overrides = {
      val newValues = values.map(_.mapMap(f))
      val changed = values.iterator.zip(newValues.iterator).exists {
        case (a, b) => a ne b
      }
      if (changed) add(values.map(_.mapMap(f)): _*)
      else this
    }
  }

  def empty: Overrides =
    Empty

  def apply(map: DependencyManagement.Map): Overrides =
    if (map.forall(_._2.isEmpty)) Empty
    else One(map.filter(!_._2.isEmpty))

  def add(overrides: Overrides*): Overrides =
    overrides.filter(_.nonEmpty) match {
      case Seq()     => Overrides.Empty
      case Seq(elem) => elem
      case more      =>
        // don't pass Compose instance around
        One(Compose(more).flatten)
    }
}
