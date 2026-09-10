package coursier.core

/** Renders case classes, optionally the way Scala 2 does.
  *
  * Scala 3 doesn't put a space after the commas separating the fields in the `toString` it
  * generates for case classes, unlike Scala 2. Tests that hash such a rendering - the resolution
  * fixture names computed in `TestHelpers` - need both to agree, hence [[scala2Mode]], in the same
  * vein as [[VariantSelector.AttributesBased.reprAsToString]]: while it is set on the current
  * thread, the classes whose `toString` calls [[ToStringHelper.apply]] render their fields the
  * Scala 2 way, whichever Scala version they were compiled with.
  */
private[coursier] object ToStringHelper {

  private final case class Probe(first: String, second: String)

  private val defaultSeparator: String =
    if (Probe("", "").toString == "Probe(, )") ", "
    else ","

  val scala2Mode: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
    override protected def initialValue(): Boolean =
      false
  }

  def apply(value: Product): String = {
    val separator = if (scala2Mode.get()) ", " else defaultSeparator
    value.productIterator.mkString(value.productPrefix + "(", separator, ")")
  }
}
