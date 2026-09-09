package dataclass

/** Scala 3 stand-in for the data-class `@since` annotation, for sources shared with Scala 2.
  *
  * Aliased to the annotation of the unroll compiler plugin (vendored under
  * `modules/unroll/plugin`), that generates the same binary-compatible overloads data-class
  * generates on Scala 2.
  */
type since = com.lihaoyi.unroll
