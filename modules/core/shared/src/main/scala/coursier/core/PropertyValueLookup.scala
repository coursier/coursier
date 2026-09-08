package coursier.core

private[coursier] trait PropertyValueLookup {
  def lookup(key: String): Option[PropertyExpr] = Option(lookupOrNull(key))
  def lookupOrNull(key: String): PropertyExpr
}

private[coursier] object PropertyValueLookup {
  def fromMap(properties: Map[String, String]): PropertyValueLookup =
    properties match {
      case pvl: PropertyValueLookup => pvl
      case _ =>
        key =>
          properties.getOrElse(key, null) match {
            case null => null
            case x    => PropertyExpr.parse(x)
          }
    }
}
