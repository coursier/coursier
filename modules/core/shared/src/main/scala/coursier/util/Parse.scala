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

}
