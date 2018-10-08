package coursier.maven

import coursier.core.Type

object MavenAttributes {

  val typeExtensions: Map[Type, String] = Map(
    Type("eclipse-plugin") -> "jar",
    Type("maven-plugin")   -> "jar",
    Type("hk2-jar")        -> "jar",
    Type("orbit")          -> "jar",
    Type("scala-jar")      -> "jar",
    Type.jar               -> "jar",
    Type.bundle            -> "jar",
    Type("doc")            -> "jar",
    Type("src")            -> "jar",
    Type.testJar           -> "jar",
    Type("ejb-client")     -> "jar"
  )

  def typeExtension(`type`: Type): String =
    typeExtensions.getOrElse(`type`, `type`.value)

  // see https://github.com/apache/maven/blob/c023e58104b71e27def0caa034d39ab0fa0373b6/maven-core/src/main/resources/META-INF/plexus/artifact-handlers.xml
  // discussed in https://github.com/coursier/coursier/issues/298
  val typeDefaultClassifiers: Map[Type, String] = Map(
    Type.testJar       -> "tests",
    Type.javadoc       -> "javadoc",
    Type.javaSource    -> "sources",
    Type("ejb-client") -> "client"
  )

  def typeDefaultClassifierOpt(`type`: Type): Option[String] =
    typeDefaultClassifiers.get(`type`)

  def typeDefaultClassifier(`type`: Type): String =
    typeDefaultClassifierOpt(`type`).getOrElse("")

  val classifierExtensionDefaultTypes: Map[(String, String), Type] = Map(
    ("tests", "jar")   -> Type.testJar,
    ("javadoc", "jar") -> Type.doc,
    ("sources", "jar") -> Type.source
    // don't know much about "client" classifier, not including it here
  )

  def classifierExtensionDefaultTypeOpt(classifier: String, ext: String): Option[Type] =
    classifierExtensionDefaultTypes.get((classifier, ext))

}
