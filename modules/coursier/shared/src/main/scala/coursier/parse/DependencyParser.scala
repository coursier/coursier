package coursier.parse

import coursier.core.{
  Attributes,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Module,
  ModuleName,
  Organization,
  Publication,
  Type
}
import coursier.core.Validation._
import coursier.util.ValidationNel
import coursier.util.Traverse._
import dependency.{NoAttributes, ScalaNameAttributes}
import dependency.parser.{DependencyParser => DepParser}

import scala.collection.mutable

/** These are not meant to be used by coursier users. Better coursier/dependency based parsers to
  * come.
  */
object DependencyParser {

  def dependency(
    input: String,
    defaultScalaVersion: String
  ): Either[String, Dependency] =
    dependency(input, defaultScalaVersion, Configuration.empty)

  def dependency(
    input: String,
    defaultScalaVersion: String,
    defaultConfiguration: Configuration
  ): Either[String, Dependency] =
    dependencyParams(input, defaultScalaVersion, defaultConfiguration).map(_._1)

  def dependencies(
    inputs: Seq[String],
    defaultScalaVersion: String
  ): ValidationNel[String, Seq[Dependency]] =
    dependencies(inputs, defaultScalaVersion, Configuration.empty)

  def dependencies(
    inputs: Seq[String],
    defaultScalaVersion: String,
    defaultConfiguration: Configuration
  ): ValidationNel[String, Seq[Dependency]] =
    dependenciesParams(inputs, defaultConfiguration, defaultScalaVersion)
      .map(_.map(_._1))

  def javaOrScalaDependencies(
    inputs: Seq[String]
  ): ValidationNel[String, Seq[JavaOrScalaDependency]] =
    javaOrScalaDependencies(inputs, Configuration.empty)

  def javaOrScalaDependencies(
    inputs: Seq[String],
    defaultConfiguration: Configuration
  ): ValidationNel[String, Seq[JavaOrScalaDependency]] =
    javaOrScalaDependenciesParams(inputs, defaultConfiguration)
      .map(_.map(_._1))

  /** Parses coordinates like org:name:version possibly with attributes, like
    * org:name;attr1=val1;attr2=val2:version
    */
  def moduleVersion(
    input: String,
    defaultScalaVersion: String
  ): Either[String, (Module, String)] = {

    val parts = input.split(":", 4)

    parts match {
      case Array(org, rawName, version) =>
        ModuleParser.module(s"$org:$rawName", defaultScalaVersion)
          .map((_, version))

      case Array(org, "", rawName, version) =>
        ModuleParser.module(s"$org::$rawName", defaultScalaVersion)
          .map((_, version))

      case _ =>
        Left(s"Malformed dependency: $input")
    }
  }

  def moduleVersions(
    inputs: Seq[String],
    defaultScalaVersion: String
  ): ValidationNel[String, Seq[(Module, String)]] =
    inputs.validationNelTraverse { input =>
      val e = moduleVersion(input, defaultScalaVersion)
      ValidationNel.fromEither(e)
    }

  /*
   * Validates the parsed attributes.
   *
   * Currently only "classifier", "type", "extension", and "url" are allowed. If more are
   * added, they should be passed in via the second parameter
   *
   * @param attrs Attributes parsed
   * @param dep String representing the dep being parsed
   * @param validAttrsKeys Valid attribute keys
   * @return A string if there is an error, otherwise None
   */
  private def validateAttributes(
    attrs: Set[String],
    dep: String,
    validAttrsKeys: Set[String]
  ): Option[String] = {
    val extraAttributes = attrs.diff(validAttrsKeys)

    if (attrs.size > validAttrsKeys.size || extraAttributes.nonEmpty)
      Some(
        s"The only attributes allowed are: ${validAttrsKeys.mkString(", ")}. ${if (extraAttributes.nonEmpty) s"The following are invalid: " +
            s"${extraAttributes.map(_ + s" in " + dep).mkString(", ")}"}"
      )
    else None
  }

  def dependencyParams(
    input: String,
    defaultScalaVersion: String
  ): Either[String, (Dependency, Map[String, String])] =
    dependencyParams(
      input,
      defaultScalaVersion,
      Configuration.empty
    )

  def javaOrScalaDependencyParams(
    input: String
  ): Either[String, (JavaOrScalaDependency, Map[String, String])] =
    javaOrScalaDependencyParams(input, Configuration.empty)

  /** Parses coordinates like org:name:version with attributes, like
    * org:name:version,attr1=val1,attr2=val2 and a configuration, like org:name:version:config or
    * org:name:version:config,attr1=val1,attr2=val2
    *
    * Currently only the "classifier" and "url attributes are used, and others throw errors.
    */
  def javaOrScalaDependencyParams(
    input: String,
    defaultConfiguration: Configuration
  ): Either[String, (JavaOrScalaDependency, Map[String, String])] =
    for {
      anyDep <- DepParser.parse(input, acceptInlineConfiguration = true)
      dep    <- JavaOrScalaDependency.from(anyDep)
      map = JavaOrScalaDependency.leftOverUserParams(anyDep)
        .map {
          case (k, v) =>
            (k, v.getOrElse(""))
        }
        .toMap
      _ <- validateAttributes(map.keySet, input, Set("url")).toLeft(())
    } yield (
      dep,
      JavaOrScalaDependency.leftOverUserParams(anyDep).map { case (k, v) => (k, v.getOrElse("")) }.toMap
    )

  /** Parses coordinates like org:name:version with attributes, like
    * org:name:version,attr1=val1,attr2=val2 and a configuration, like org:name:version:config or
    * org:name:version:config,attr1=val1,attr2=val2
    *
    * Currently only the "classifier", "type", "extension", and "url attributes are used, and others
    * throw errors.
    */
  def dependencyParams(
    input: String,
    defaultScalaVersion: String,
    defaultConfiguration: Configuration
  ): Either[String, (Dependency, Map[String, String])] =
    javaOrScalaDependencyParams(input, defaultConfiguration).map {
      case (dep, params) =>
        (dep.dependency(defaultScalaVersion), params)
    }

  def dependenciesParams(
    inputs: Seq[String],
    defaultConfiguration: Configuration,
    defaultScalaVersion: String
  ): ValidationNel[String, Seq[(Dependency, Map[String, String])]] =
    inputs.validationNelTraverse { input =>
      val e = dependencyParams(input, defaultScalaVersion, defaultConfiguration)
      ValidationNel.fromEither(e)
    }

  def javaOrScalaDependenciesParams(
    inputs: Seq[String]
  ): ValidationNel[String, Seq[(JavaOrScalaDependency, Map[String, String])]] =
    javaOrScalaDependenciesParams(inputs, Configuration.empty)

  def javaOrScalaDependenciesParams(
    inputs: Seq[String],
    defaultConfiguration: Configuration
  ): ValidationNel[String, Seq[(JavaOrScalaDependency, Map[String, String])]] =
    inputs.validationNelTraverse { input =>
      val e = javaOrScalaDependencyParams(input, defaultConfiguration)
      ValidationNel.fromEither(e)
    }

}
