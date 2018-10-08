package coursier.maven

import coursier.core.{Classifier, Extension, Type}

object MavenAttributes {

  val typeExtensions: Map[Type, Extension] = Map(
    Type("eclipse-plugin") -> Extension.jar,
    Type("maven-plugin")   -> Extension.jar,
    Type("hk2-jar")        -> Extension.jar,
    Type("orbit")          -> Extension.jar,
    Type("scala-jar")      -> Extension.jar,
    Type.jar               -> Extension.jar,
    Type.bundle            -> Extension.jar,
    Type("doc")            -> Extension.jar,
    Type("src")            -> Extension.jar,
    Type.testJar           -> Extension.jar,
    Type("ejb-client")     -> Extension.jar
  )

  def typeExtension(`type`: Type): Extension =
    typeExtensions.getOrElse(`type`, `type`.asExtension)

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

  val classifierExtensionDefaultTypes: Map[(Classifier, Extension), Type] = Map(
    (Classifier.tests, Extension.jar)   -> Type.testJar,
    (Classifier.javadoc, Extension.jar) -> Type.doc,
    (Classifier.sources, Extension.jar) -> Type.source
    // don't know much about "client" classifier, not including it here
  )

  def classifierExtensionDefaultTypeOpt(classifier: Classifier, ext: Extension): Option[Type] =
    classifierExtensionDefaultTypes.get((classifier, ext))

}
