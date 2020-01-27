package coursier.parse

import coursier.core.{Attributes, Classifier, Configuration, Dependency, Extension, Module, ModuleName, Organization, Publication, Type}
import coursier.util.ValidationNel
import coursier.util.Traverse._

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
      Configuration.empty
    )

  def javaOrScalaDependencyParams(
    input: String
  ): Either[String, (JavaOrScalaDependency, Map[String, String])] =
    javaOrScalaDependencyParams(input, Configuration.empty)

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

    val (coords, rawAttrs) = input.split(":", 6) match {
      case Array(org, "", "", name, "", rest) =>
        val (coordsEnd, attrs) = splitRest(rest)
        (s"$org:::$name::$coordsEnd", attrs)
      case Array(org, "", name, "", rest) =>
        val (coordsEnd, attrs) = splitRest(rest)
        (s"$org::$name::$coordsEnd", attrs)
      case Array(org, "", "", name, rest) =>
        val (coordsEnd, attrs) = splitRest(rest)
        (s"$org:::$name:$coordsEnd", attrs)
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
        case Left(err) =>
          // FIXME We're dropping other errors here (use validation in return type?)
          Left(err)
      }
      .getOrElse {

        val attrs = attrsOrErrors
          .collect {
            case Right(attr) => attr
          }
          .toMap

        val parts = coords.split(":", -1)

        // Only attributes allowed
        val validAttrsKeys = Set("classifier", "ext", "type", "url")

        validateAttributes(attrs, input, validAttrsKeys) match {
          case Some(err) => Left(err)
          case None =>

            val type0 = attrs
              .get("type")
              .map(Type(_))
              .getOrElse(Type.empty)
            val ext = attrs
              .get("ext")
              .map(Extension(_))
              .getOrElse(Extension.empty)
            val classifier = attrs
              .get("classifier")
              .map(Classifier(_))
              .getOrElse(Classifier.empty)
            val publication = Publication("", type0, ext, classifier)

            val extraDependencyParams: Map[String, String] = attrs.get("url") match {
                case Some(url) => Map("url" -> url)
                case None => Map()
              }

            val dummyModule = Module(Organization(""), ModuleName(""), Map.empty)

            val parts0 = parts match {
              case Array(org, "", "", rawName, "", version, config) =>
                Right((org, rawName, version, Configuration(config), ":::", true))

              case Array(org, "", "", rawName, "", version) =>
                Right((org, rawName, version, defaultConfiguration, ":::", true))

              case Array(org, "", rawName, "", version, config) =>
                Right((org, rawName, version, Configuration(config), "::", true))

              case Array(org, "", rawName, "", version) =>
                Right((org, rawName, version, defaultConfiguration, "::", true))

              case Array(org, "", "", rawName, version, config) =>
                Right((org, rawName, version, Configuration(config), ":::", false))

              case Array(org, "", "", rawName, version) =>
                Right((org, rawName, version, defaultConfiguration, ":::", false))

              case Array(org, "", rawName, version, config) =>
                Right((org, rawName, version, Configuration(config), "::", false))

              case Array(org, "", rawName, version) =>
                Right((org, rawName, version, defaultConfiguration, "::", false))

              case Array(org, rawName, version, config) =>
                Right((org, rawName, version, Configuration(config), ":", false))

              case Array(org, rawName, version) =>
                Right((org, rawName, version, defaultConfiguration, ":", false))

              case _ =>
                Left(s"Malformed dependency: $input")
            }

            parts0.flatMap {
              case (org, rawName, version, config, orgNameSep, withPlatformSuffix) =>
                ModuleParser.javaOrScalaModule(s"$org$orgNameSep$rawName")
                  .map { mod =>
                    val dep = Dependency(
                      dummyModule,
                      version,
                      config,
                      Set.empty[(Organization, ModuleName)],
                      publication,
                      optional = false,
                      transitive = true
                    )
                    val dep0 = JavaOrScalaDependency(mod, dep)
                    val dep1 =
                      if (withPlatformSuffix)
                        dep0 match {
                          case j: JavaOrScalaDependency.JavaDependency => j
                          case s: JavaOrScalaDependency.ScalaDependency =>
                            s.copy(withPlatformSuffix = true)
                        }
                      else
                        dep0
                    (dep1, extraDependencyParams)
                  }
            }
        }
    }
  }

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
    *  Currently only the "classifier", "type", "extension", and "url attributes are
    *  used, and others throw errors.
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
