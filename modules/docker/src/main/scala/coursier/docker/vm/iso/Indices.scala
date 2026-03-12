package coursier.docker.vm.iso

sealed abstract class Indices {
  def apply(rec: Records): Int
}

object Indices {
  private final case class Impl(map: Map[Records, Int]) extends Indices {
    def apply(rec: Records): Int = map(rec)
  }
  private case object Zeros extends Indices {
    def apply(rec: Records): Int = 0
  }

  def apply(map: Map[Records, Int]): Indices =
    Impl(map)
  def zeros: Indices =
    Zeros
}
