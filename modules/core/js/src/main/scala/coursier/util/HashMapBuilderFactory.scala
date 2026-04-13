package coursier.util

private[coursier] object HashMapBuilderFactory {
  def apply[A, B <: AnyRef]: HashMapBuilder[A, B] = new CompatibleHashMapBuilder[A, B]
}
