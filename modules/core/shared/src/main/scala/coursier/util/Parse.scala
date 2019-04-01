package coursier.util

import coursier.core._

import scala.collection.mutable.ArrayBuffer

object Parse {

  /**
    * Parses a module like
    *   org:name
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2
    *
    * Two semi-columns after the org part is interpreted as a scala module. E.g. if
    * `defaultScalaVersion` is `"2.11.x"`, org::name:ver is equivalent to org:name_2.11:ver.
    */
  def module(s: String, defaultScalaVersion: String): Either[String, Module] = {

    val parts = s.split(":", 3)

    val values = parts match {
      case Array(org, rawName) =>
        Right((Organization(org), rawName, ""))
      case Array(org, "", rawName) =>
        Right((Organization(org), rawName, "_" + defaultScalaVersion.split('.').take(2).mkString(".")))
      case _ =>
        Left(s"malformed module: $s")
    }

    values.right.flatMap {
      case (org, rawName, suffix) =>

        val splitName = rawName.split(';')

        if (splitName.tail.exists(!_.contains("=")))
          Left(s"malformed attribute(s) in $s")
        else {
          val name = splitName.head
          val attributes = splitName.tail.map(_.split("=", 2)).map {
            case Array(key, value) => key -> value
          }.toMap

          Right(Module(org, ModuleName(name + suffix), attributes))
        }
    }
  }

  private def valuesAndErrors[L, R](f: String => Either[L, R], l: Seq[String]): (Seq[L], Seq[R]) = {

    val errors = new ArrayBuffer[L]
    val values = new ArrayBuffer[R]

    for (elem <- l)
      f(elem) match {
        case Left(err) => errors += err
        case Right(modVer) => values += modVer
      }

    (errors.toSeq, values.toSeq)
  }

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules/versions
    */
  def modules(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[Module]) =
    valuesAndErrors(module(_, defaultScalaVersion), l)

  /**
    * Parses coordinates like
    *   org:name:version
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2:version
    */
  def moduleVersion(s: String, defaultScalaVersion: String): Either[String, (Module, String)] = {

    val parts = s.split(":", 4)

    parts match {
      case Array(org, rawName, version) =>
         module(s"$org:$rawName", defaultScalaVersion)
           .right
           .map((_, version))

      case Array(org, "", rawName, version) =>
        module(s"$org::$rawName", defaultScalaVersion)
          .right
          .map((_, version))

      case _ =>
        Left(s"Malformed dependency: $s")
    }
  }

  class ModuleParseError(private val message: String = "",
                              private val cause: Throwable = None.orNull)
    extends Exception(message, cause)

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
  def moduleVersionConfig(s: String,
                          req: ModuleRequirements,
                          transitive: Boolean,
                          defaultScalaVersion: String): Either[String, (Dependency, Map[String, String])] = {

    // FIXME Fails to parse dependencies with version intervals, because of the comma in the interval
    // e.g. "joda-time:joda-time:[2.2,2.8]"

    // Assume org:name:version,attr1=val1,attr2=val2
    // That is ',' has to go after ':'.
    // E.g. "org:name,attr1=val1,attr2=val2:version:config" is illegal.
    val attrSeparator = ","
    val argSeparator = ":"

    val Array(coords, rawAttrs @ _*) = s.split(attrSeparator)

    val attrsOrErrors = rawAttrs
      .map { x =>
        if (x.contains(argSeparator))
          Left(s"'$argSeparator' is not allowed in attribute '$x' in '$s'. Please follow the format " +
            s"'org${argSeparator}name[${argSeparator}version][${argSeparator}config]${attrSeparator}attr1=val1${attrSeparator}attr2=val2'")
        else
          x.split("=") match {
            case Array(k, v) =>
              Right(k -> v)
            case _ =>
              Left(s"Failed to parse attribute '$x' in '$s'. Keyword argument expected such as 'classifier=tests'")
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

        val parts = coords.split(":", 5)

        // Only "classifier" and "url" attributes are allowed
        val validAttrsKeys = Set("classifier", "url")

        validateAttributes(attrs, s, validAttrsKeys) match {
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

            val localExcludes = req.localExcludes
            val globalExcludes = req.globalExcludes
            val defaultConfig = req.defaultConfiguration

            val depOrError = parts match {
              case Array(org, "", rawName, version, config) =>
                module(s"$org::$rawName", defaultScalaVersion)
                  .right
                  .map(mod =>
                    Dependency(
                      mod,
                      version,
                      Configuration(config),
                      localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes,
                      attributes,
                      optional = false,
                      transitive = transitive
                    )
                  )

              case Array(org, "", rawName, version) =>
                module(s"$org::$rawName", defaultScalaVersion)
                  .right
                  .map(mod =>
                    Dependency(
                      mod,
                      version,
                      defaultConfig,
                      localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes,
                      attributes,
                      optional = false,
                      transitive
                    )
                  )

              case Array(org, rawName, version, config) =>
                module(s"$org:$rawName", defaultScalaVersion)
                  .right
                  .map(mod =>
                    Dependency(
                      mod,
                      version,
                      Configuration(config),
                      localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes,
                      attributes,
                      optional = false,
                      transitive
                    )
                  )

              case Array(org, rawName, version) =>
                module(s"$org:$rawName", defaultScalaVersion)
                  .right
                  .map(mod =>
                    Dependency(
                      mod,
                      version,
                      defaultConfig,
                      localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes,
                      attributes,
                      optional = false,
                      transitive
                    )
                  )

              case _ =>
                Left(s"Malformed dependency: $s")
            }

            depOrError.right.map(dep => (dep, extraDependencyParams))
        }
    }
  }

  /**
   * Validates the parsed attributes
   *
   * Currently only "classifier" and "url" are allowed. If more are
   * added, they should be passed in via the second parameter
   *
   * @param attrs Attributes parsed
   * @param dep String representing the dep being parsed
   * @param validAttrsKeys Valid attribute keys
   * @return A string if there is an error, otherwise None
   */
  private def validateAttributes(attrs: Map[String, String],
                                 dep: String,
                                 validAttrsKeys: Set[String]): Option[String] = {
    val extraAttributes = attrs.keys.toSet.diff(validAttrsKeys)

    if (attrs.size > validAttrsKeys.size || extraAttributes.nonEmpty)
      Some(s"The only attributes allowed are: ${validAttrsKeys.mkString(", ")}. ${
        if (extraAttributes.nonEmpty) s"The following are invalid: " +
          s"${extraAttributes.map(_ + s" in "+ dep).mkString(", ")}"
      }")
    else None
  }

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules / versions
    */
  def moduleVersions(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[(Module, String)]) =
    valuesAndErrors(moduleVersion(_, defaultScalaVersion), l)

  /**
    * Data holder for additional info that needs to be considered when parsing the module.
    *
    * @param globalExcludes global excludes that need to be applied to all modules
    * @param localExcludes excludes to be applied to specific modules
    * @param defaultConfiguration default configuration
    */
  case class ModuleRequirements(globalExcludes: Set[(Organization, ModuleName)] = Set(),
                                localExcludes: Map[String, Set[(Organization, ModuleName)]] = Map(),
                                defaultConfiguration: Configuration = Configuration.defaultCompile)

  /**
    * Parses a sequence of coordinates having an optional configuration.
    *
    * @return Sequence of errors, and sequence of modules / versions / optional configurations
    */
  def moduleVersionConfigs(l: Seq[String],
                           req: ModuleRequirements,
                           transitive: Boolean,
                           defaultScalaVersion: String): (Seq[String], Seq[(Dependency, Map[String, String])]) =
    valuesAndErrors(moduleVersionConfig(_, req, transitive, defaultScalaVersion), l)

}
