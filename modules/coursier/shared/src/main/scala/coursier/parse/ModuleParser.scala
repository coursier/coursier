package coursier.parse

import coursier.core.{Module, ModuleName, Organization}
import coursier.util.Traverse._
import coursier.util.ValidationNel
import coursier.core.Validation._

object ModuleParser {

  /** Parses a module like org:name possibly with attributes, like org:name;attr1=val1;attr2=val2
    *
    * Two semi-columns after the org part is interpreted as a scala module. E.g. if the scala
    * version is 2.11., org::name is equivalent to org:name_2.11.
    */
  def javaOrScalaModule(s: String): Either[String, JavaOrScalaModule] = {

    val parts = s.split(":", -1)

    val values = parts match {
      case Array(org, rawName)         => Right((org, rawName, None))
      case Array(org, "", rawName)     => Right((org, rawName, Some(false)))
      case Array(org, "", "", rawName) => Right((org, rawName, Some(true)))
      case _                           => Left(s"malformed module: $s")
    }

    for {
      values <- values
      (rawOrg, rawName, scalaFullVerOpt) = values
      org <- validateCoordinate(rawOrg, "organization")
      splitName = rawName.split(';')
      name <-
        if (splitName.tail.exists(!_.contains("="))) Left(s"malformed attribute(s) in $s")
        else validateCoordinate(splitName.head, "module name")
    } yield {
      val attributes = splitName.tail.map(_.split("=", 2)).map {
        case Array(key, value) => key -> value
      }.toMap

      val baseModule = Module(Organization(org), ModuleName(name), attributes)

      scalaFullVerOpt match {
        case None               => JavaOrScalaModule.JavaModule(baseModule)
        case Some(scalaFullVer) => JavaOrScalaModule.ScalaModule(baseModule, scalaFullVer)
      }
    }
  }

  /** Parses a module like org:name possibly with attributes, like org:name;attr1=val1;attr2=val2
    *
    * Two semi-columns after the org part is interpreted as a scala module. E.g. if
    * `defaultScalaVersion` is `"2.11.x"`, org::name is equivalent to org:name_2.11.
    */
  def module(s: String, defaultScalaVersion: String): Either[String, Module] =
    javaOrScalaModule(s).map(_.module(defaultScalaVersion))

  def javaOrScalaModules(
    inputs: Seq[String]
  ): ValidationNel[String, Seq[JavaOrScalaModule]] =
    inputs.validationNelTraverse { input =>
      val e = javaOrScalaModule(input)
      ValidationNel.fromEither(e)
    }

  def modules(
    inputs: Seq[String],
    defaultScalaVersion: String
  ): ValidationNel[String, Seq[Module]] =
    javaOrScalaModules(inputs)
      .map(_.map(_.module(defaultScalaVersion)))

}
