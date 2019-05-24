package coursier.parse

import coursier.core.{Attributes, Classifier, Configuration, Dependency, Module, ModuleName, Organization, Type}
import coursier.util.ValidationNel
import coursier.util.Traverse._

object DependencyParser {

  def dependency(
    input: String,
    defaultScalaVersion: String
  ): Either[String, Dependency] =
    dependency(input, defaultScalaVersion, Configuration.defaultCompile)

  def dependency(
    input: String,
    defaultScalaVersion: String,
    defaultConfiguration: Configuration
  ): Either[String, Dependency] =
    dependencyParams(input, defaultScalaVersion, defaultConfiguration)
      .right
      .map(_._1)

  def dependencies(
    inputs: Seq[String],
    defaultScalaVersion: String
  ): ValidationNel[String, Seq[Dependency]] =
    dependencies(inputs, defaultScalaVersion, Configuration.defaultCompile)

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
    javaOrScalaDependencies(inputs, Configuration.defaultCompile)

  def javaOrScalaDependencies(
    inputs: Seq[String],
    defaultConfiguration: Configuration
  ): ValidationNel[String, Seq[JavaOrScalaDependency]] =
    javaOrScalaDependenciesParams(inputs, defaultConfiguration)
      .map(_.map(_._1))


  /**
    * Parses coordinates like
    *   org:name:version
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2:version
    */
  def moduleVersion(input: String, defaultScalaVersion: String): Either[String, (Module, String)] = {

    val parts = input.split(":", 4)

    parts match {
      case Array(org, rawName, version) =>
        ModuleParser.module(s"$org:$rawName", defaultScalaVersion)
           .right
           .map((_, version))

      case Array(org, "", rawName, version) =>
        ModuleParser.module(s"$org::$rawName", defaultScalaVersion)
          .right
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
   * Currently only "classifier" and "url" are allowed. If more are
   * added, they should be passed in via the second parameter
   *
   * @param attrs Attributes parsed
   * @param dep String representing the dep being parsed
   * @param validAttrsKeys Valid attribute keys
   * @return A string if there is an error, otherwise None
   */
  private def validateAttributes(
    attrs: Map[String, String],
    dep: String,
    validAttrsKeys: Set[String]
  ): Option[String] = {
    val extraAttributes = attrs.keys.toSet.diff(validAttrsKeys)

    if (attrs.size > validAttrsKeys.size || extraAttributes.nonEmpty)
      Some(s"The only attributes allowed are: ${validAttrsKeys.mkString(", ")}. ${
        if (extraAttributes.nonEmpty) s"The following are invalid: " +
          s"${extraAttributes.map(_ + s" in "+ dep).mkString(", ")}"
      }")
    else None
  }

  def dependencyParams(
    input: String,
    defaultScalaVersion: String
  ): Either[String, (Dependency, Map[String, String])] =
    dependencyParams(
      input,
      defaultScalaVersion,
      Configuration.defaultCompile
    )

  /**
    * Parses coordinates like
    *   org:name:version
    *  with attributes, like
    *   org:name:version,attr1=val1,attr2=val2
    *  and a configuration, like
    *   org:name:version:config
    *  or
    *   org:name:version:config,attr1=val1,attr2=val2
    *
    *  Currently only the "classifier" and "url attributes are
    *  used, and others throw errors.
    */
  def javaOrScalaDependencyParams(
    input: String,
    defaultConfiguration: Configuration
  ): Either[String, (JavaOrScalaDependency, Map[String, String])] = {

    // FIXME Fails to parse dependencies with version intervals, because of the comma in the interval
    // e.g. "joda-time:joda-time:[2.2,2.8]"

    // Assume org:name:version,attr1=val1,attr2=val2
    // That is ',' has to go after ':'.
    // E.g. "org:name,attr1=val1,attr2=val2:version:config" is illegal.
    val attrSeparator = ","
    val argSeparator = ":"

    def splitRest(rest: String): (String, Seq[String]) = {

      def split(rest: String) =
        // match is total
        rest.split(attrSeparator) match {
          case Array(coordsEnd, attrs @ _*) => (coordsEnd, attrs)
        }

      if (rest.startsWith("[") || rest.startsWith("(")) {
        val idx = rest.indexWhere(c => c == ']' || c == ')')
        if (idx < 0)
          split(rest)
        else {
          val (ver, attrsPart) = rest.splitAt(idx + 1)
          val (coodsEnd, attrs) = split(attrsPart)
          (ver + coodsEnd, attrs)
        }
      } else
        split(rest)
    }

    val (coords, rawAttrs) = input.split(":", 4) match {
      case Array(org, "", name, rest) =>
        val (coordsEnd, attrs) = splitRest(rest)
        (s"$org::$name:$coordsEnd", attrs)
      case Array(org, name, rest @ _*) =>
        val (coordsEnd, attrs) = splitRest(rest.mkString(":"))
        (s"$org:$name:$coordsEnd", attrs)
    }

    val attrsOrErrors = rawAttrs
      .map { x =>
        if (x.contains(argSeparator))
          Left(s"'$argSeparator' is not allowed in attribute '$x' in '$input'. Please follow the format " +
            s"'org${argSeparator}name[${argSeparator}version][${argSeparator}config]${attrSeparator}attr1=val1${attrSeparator}attr2=val2'")
        else
          x.split("=") match {
            case Array(k, v) =>
              Right(k -> v)
            case _ =>
              Left(s"Failed to parse attribute '$x' in '$input'. Keyword argument expected such as 'classifier=tests'")
          }
      }

    attrsOrErrors
      .collectFirst {
        case Left(err) => Left(err)
      }
      .getOrElse {

        val attrs = attrsOrErrors
          .collect {
            case Right(attr) => attr
          }
          .toMap

        val parts = coords.split(":", -1)

        // Only "classifier" and "url" attributes are allowed
        val validAttrsKeys = Set("classifier", "url")

        validateAttributes(attrs, input, validAttrsKeys) match {
          case Some(err) => Left(err)
          case None =>

            val attributes = attrs.get("classifier") match {
              case Some(c) => Attributes(Type.empty, Classifier(c))
              case None => Attributes(Type.empty, Classifier.empty)
            }

            val extraDependencyParams: Map[String, String] = attrs.get("url") match {
                case Some(url) => Map("url" -> url)
                case None => Map()
              }

            val dummyModule = Module(Organization(""), ModuleName(""), Map.empty)

            val parts0 = parts match {
              case Array(org, "", "", rawName, version, config) =>
                Right((org, rawName, version, Configuration(config), ":::"))

              case Array(org, "", "", rawName, version) =>
                Right((org, rawName, version, defaultConfiguration, ":::"))

              case Array(org, "", rawName, version, config) =>
                Right((org, rawName, version, Configuration(config), "::"))

              case Array(org, "", rawName, version) =>
                Right((org, rawName, version, defaultConfiguration, "::"))

              case Array(org, rawName, version, config) =>
                Right((org, rawName, version, Configuration(config), ":"))

              case Array(org, rawName, version) =>
                Right((org, rawName, version, defaultConfiguration, ":"))

              case _ =>
                Left(s"Malformed dependency: $input")
            }

            parts0.right.flatMap {
              case (org, rawName, version, config, orgNameSep) =>
                ModuleParser.javaOrScalaModule(s"$org$orgNameSep$rawName")
                  .right
                  .map { mod =>
                    val dep = Dependency(
                      dummyModule,
                      version,
                      config,
                      Set(),
                      attributes,
                      optional = false,
                      transitive = true
                    )
                    (JavaOrScalaDependency(mod, dep), extraDependencyParams)
                  }
            }
        }
    }
  }

  def javaOrScalaDependencyParams(
    input: String
  ): Either[String, (JavaOrScalaDependency, Map[String, String])] =
    javaOrScalaDependencyParams(
      input,
      Configuration.defaultCompile
    )

  /**
    * Parses coordinates like
    *   org:name:version
    *  with attributes, like
    *   org:name:version,attr1=val1,attr2=val2
    *  and a configuration, like
    *   org:name:version:config
    *  or
    *   org:name:version:config,attr1=val1,attr2=val2
    *
    *  Currently only the "classifier" and "url attributes are
    *  used, and others throw errors.
    */
  def dependencyParams(
    input: String,
    defaultScalaVersion: String,
    defaultConfiguration: Configuration
  ): Either[String, (Dependency, Map[String, String])] =
    javaOrScalaDependencyParams(input, defaultConfiguration)
      .right.map {
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
    inputs: Seq[String],
    defaultConfiguration: Configuration
  ): ValidationNel[String, Seq[(JavaOrScalaDependency, Map[String, String])]] =
    inputs.validationNelTraverse { input =>
      val e = javaOrScalaDependencyParams(input, defaultConfiguration)
      ValidationNel.fromEither(e)
    }

}
