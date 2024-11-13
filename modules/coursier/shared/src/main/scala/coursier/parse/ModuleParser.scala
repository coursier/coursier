package coursier.parse

import coursier.core.{Module, ModuleName, Organization}
import coursier.util.Traverse._
import coursier.util.ValidationNel
import coursier.core.Validation._
import dependency.{NoAttributes, ScalaNameAttributes}

object ModuleParser {

  /** Parses a module like org:name possibly with attributes, like org:name;attr1=val1;attr2=val2
    *
    * Two semi-columns after the org part is interpreted as a scala module. E.g. if the scala
    * version is 2.11., org::name is equivalent to org:name_2.11.
    */
  def javaOrScalaModule(s: String): Either[String, JavaOrScalaModule] =
    dependency.parser.ModuleParser.parse(s).flatMap { anyMod =>
      (
        validateCoordinate(anyMod.organization, "organization"),
        validateCoordinate(anyMod.name, "module name")
      ) match {
        case (Right(org), Right(name)) =>
          var csMod = Module(Organization(org), ModuleName(name), anyMod.attributes)
          val res = anyMod.nameAttributes match {
            case NoAttributes =>
              JavaOrScalaModule.JavaModule(csMod)
            case scalaAttr: ScalaNameAttributes =>
              JavaOrScalaModule.ScalaModule(csMod, scalaAttr.fullCrossVersion.getOrElse(false))
          }
          Right(res)
        case (a, b) =>
          Left(Seq(a, b).collect { case Left(v) => v }.mkString(", "))
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
