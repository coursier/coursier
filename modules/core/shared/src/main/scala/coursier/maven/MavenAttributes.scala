package coursier.maven

object MavenAttributes {

  val typeExtensions: Map[String, String] = Map(
    "eclipse-plugin" -> "jar",
    "maven-plugin"   -> "jar",
    "hk2-jar"        -> "jar",
    "orbit"          -> "jar",
    "scala-jar"      -> "jar",
    "jar"            -> "jar",
    "bundle"         -> "jar",
    "doc"            -> "jar",
    "src"            -> "jar",
    "test-jar"       -> "jar",
    "ejb-client"     -> "jar"
  )

  def typeExtension(`type`: String): String =
    typeExtensions.getOrElse(`type`, `type`)

  // see https://github.com/apache/maven/blob/c023e58104b71e27def0caa034d39ab0fa0373b6/maven-core/src/main/resources/META-INF/plexus/artifact-handlers.xml
  // discussed in https://github.com/coursier/coursier/issues/298
  val typeDefaultClassifiers: Map[String, String] = Map(
    "test-jar"    -> "tests",
    "javadoc"     -> "javadoc",
    "java-source" -> "sources",
    "ejb-client"  -> "client"
  )

  def typeDefaultClassifierOpt(`type`: String): Option[String] =
    typeDefaultClassifiers.get(`type`)

  def typeDefaultClassifier(`type`: String): String =
    typeDefaultClassifierOpt(`type`).getOrElse("")

  val classifierExtensionDefaultTypes: Map[(String, String), String] = Map(
    ("tests", "jar")   -> "test-jar",
    ("javadoc", "jar") -> "doc",
    ("sources", "jar") -> "src"
    // don't know much about "client" classifier, not including it here
  )

  def classifierExtensionDefaultTypeOpt(classifier: String, ext: String): Option[String] =
    classifierExtensionDefaultTypes.get((classifier, ext))

}
