package coursier.parse

import coursier.core.{Module, ModuleName, Organization}
import coursier.util.Traverse._
import coursier.util.ValidationNel

object ModuleParser {

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

  def modules(
    inputs: Seq[String],
    defaultScalaVersion: String
  ): ValidationNel[String, Seq[Module]] =
    inputs.validationNelTraverse { input =>
      val e = module(input, defaultScalaVersion)
      ValidationNel.fromEither(e)
    }

}
