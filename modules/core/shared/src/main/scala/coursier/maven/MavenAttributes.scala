package coursier.maven

import coursier.core.{Classifier, Type}

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
  val typeDefaultClassifiers: Map[Type, Classifier] = Map(
    Type.testJar       -> Classifier.tests,
    Type.javadoc       -> Classifier.javadoc,
    Type.javaSource    -> Classifier.sources,
    Type("ejb-client") -> Classifier("client")
  )

  def typeDefaultClassifierOpt(`type`: Type): Option[Classifier] =
    typeDefaultClassifiers.get(`type`)

  def typeDefaultClassifier(`type`: Type): Classifier =
    typeDefaultClassifierOpt(`type`).getOrElse(Classifier.empty)

  val classifierExtensionDefaultTypes: Map[(Classifier, String), Type] = Map(
    (Classifier.tests, "jar")   -> Type.testJar,
    (Classifier.javadoc, "jar") -> Type.doc,
    (Classifier.sources, "jar") -> Type.source
    // don't know much about "client" classifier, not including it here
  )

  def classifierExtensionDefaultTypeOpt(classifier: Classifier, ext: String): Option[Type] =
    classifierExtensionDefaultTypes.get((classifier, ext))

}
