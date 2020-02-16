package coursier.ivy

import coursier.util.Traverse.TraverseOps
import coursier.util.ValidationNel
import dataclass.data
import fastparse._, NoWhitespace._

@data class PropertiesPattern(chunks: Seq[PropertiesPattern.ChunkOrProperty]) {

  def string: String = chunks.map(_.string).mkString

  import PropertiesPattern.ChunkOrProperty

  def substituteProperties(properties: Map[String, String]): Either[String, Pattern] = {

    val validation = chunks.validationNelTraverse[String, Seq[Pattern.Chunk]] {
      case prop: ChunkOrProperty.Prop => //(name, alternativesOpt) =>
        properties.get(prop.name) match {
          case Some(value) =>
            ValidationNel.success(Seq(Pattern.Chunk.Const(value)))
          case None =>
            prop.alternative match {
              case Some(alt) =>
                ValidationNel.fromEither(
                  PropertiesPattern(alt)
                    .substituteProperties(properties)
                    .map(_.chunks.toVector)
                )
              case None =>
                ValidationNel.failure(prop.name)
            }
        }

      case opt: ChunkOrProperty.Opt =>
        ValidationNel.fromEither(
          PropertiesPattern(opt.content)
            .substituteProperties(properties)
            .map(l => Seq(Pattern.Chunk.Opt(l.chunks)))
        )

      case v: ChunkOrProperty.Var =>
        ValidationNel.success(Seq(Pattern.Chunk.Var(v.name)))

      case c: ChunkOrProperty.Const =>
        ValidationNel.success(Seq(Pattern.Chunk.Const(c.value)))

    }.map(c => Pattern(c.flatten))

    validation.either.left.map { notFoundProps =>
      s"Property(ies) not found: ${notFoundProps.mkString(", ")}"
    }
  }
}

@data class Pattern(chunks: Seq[Pattern.Chunk]) {

  def +:(chunk: Pattern.Chunk): Pattern =
    Pattern(chunk +: chunks)

  import Pattern.Chunk

  def string: String = chunks.map(_.string).mkString

  def substituteVariables(variables: Map[String, String]): Either[String, String] = {

    def helper(chunks: Seq[Chunk]): ValidationNel[String, Seq[Chunk.Const]] =
      chunks.validationNelTraverse[String, Seq[Chunk.Const]] {
        case v: Chunk.Var =>
          variables.get(v.name) match {
            case Some(value) =>
              ValidationNel.success(Seq(Chunk.Const(value)))
            case None =>
              ValidationNel.failure(v.name)
          }
        case opt: Chunk.Opt =>
          val res = helper(opt.content)
          if (res.isSuccess)
            res
          else
            ValidationNel.success(Seq())
        case c: Chunk.Const =>
          ValidationNel.success(Seq(c))
      }.map(_.flatten)

    val validation = helper(chunks)

    validation.either match {
      case Left(notFoundVariables) =>
        Left(s"Variables not found: ${notFoundVariables.mkString(", ")}")
      case Right(constants) =>
        val b = new StringBuilder
        constants.foreach(b ++= _.value)
        Right(b.result())
    }
  }


  def substitute(varName: String, replacement: Seq[Chunk]): Pattern =
    Pattern(
      chunks.flatMap {
        case v: Chunk.Var if v.name == varName => replacement
        case opt: Chunk.Opt => Seq(Chunk.Opt(Pattern(opt.content).substitute(varName, replacement).chunks))
        case c => Seq(c)
      }
    )

  def substituteDefault: Pattern =
    substitute("defaultPattern", Pattern.default.chunks)

  private lazy val constStart = chunks
    .iterator
    .map {
      case c: Pattern.Chunk.Const => Some(c.value)
      case _ => None
    }
    .takeWhile(_.nonEmpty)
    .flatten
    .mkString

  def startsWith(s: String): Boolean =
    constStart.startsWith(s)
  def stripPrefix(s: String): Pattern =
    Pattern(
      Pattern.Chunk.Const(constStart.stripPrefix(s)) +:
        chunks.dropWhile { case _: Pattern.Chunk.Const => true; case _ => false }
    )
  def addPrefix(s: String): Pattern =
    Pattern(Pattern.Chunk.Const(s) +: chunks)
}

object PropertiesPattern {

  sealed abstract class ChunkOrProperty extends Product with Serializable {
    def string: String
  }

  object ChunkOrProperty {
    @data class Prop(name: String, alternative: Option[Seq[ChunkOrProperty]]) extends ChunkOrProperty {
      def string: String =
      s"$${" + name + alternative.fold("")(alt => "-" + alt.map(_.string).mkString) + "}"
    }
    @data class Var(name: String) extends ChunkOrProperty {
      def string: String = "[" + name + "]"
    }
    @data class Opt(content: Seq[ChunkOrProperty]) extends ChunkOrProperty {
      def string: String = "(" + content.map(_.string).mkString + ")"
    }
    object Opt {
      def apply(elem: ChunkOrProperty): Opt =
        Opt(Seq(elem))
      def apply(elem: ChunkOrProperty, elem1: ChunkOrProperty): Opt =
        Opt(Seq(elem, elem1))
      def apply(elem: ChunkOrProperty, elem1: ChunkOrProperty, elem2: ChunkOrProperty): Opt =
        Opt(Seq(elem, elem1, elem2))
    }
    @data class Const(value: String) extends ChunkOrProperty {
      def string: String = value
    }

    implicit def fromString(s: String): ChunkOrProperty = Const(s)
  }

  private def parser[_: P]: P[Seq[ChunkOrProperty]] = {

    val notIn = s"[]{}()$$".toSet
    def chars = P(CharsWhile(c => !notIn(c)).!)
    def noHyphenChars = P(CharsWhile(c => !notIn(c) && c != '-').!)

    def constant: P[ChunkOrProperty.Const] =
      P(chars.!)
        .map(ChunkOrProperty.Const(_))

    def property: P[ChunkOrProperty.Prop] =
      P(s"$${" ~ noHyphenChars.! ~ ("-" ~ chunks).? ~ "}")
        .map { case (name, altOpt) => ChunkOrProperty.Prop(name, altOpt) }

    def variable: P[ChunkOrProperty.Var] =
      P("[" ~ chars.! ~ "]")
        .map(ChunkOrProperty.Var(_))

    def optional: P[ChunkOrProperty.Opt] =
      P("(" ~ chunks ~ ")")
        .map(l => ChunkOrProperty.Opt(l))

    def chunks: P[Seq[ChunkOrProperty]] =
      P((constant | property | variable | optional).rep)
        .map(_.toVector) // "Vector" is more readable than "ArrayBuffer"

    chunks
  }


  def parse(pattern: String): Either[String, PropertiesPattern] =
    fastparse.parse(pattern, parser(_)) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(v, _) =>
        Right(PropertiesPattern(v))
    }

}

object Pattern {

  sealed abstract class Chunk extends Product with Serializable {
    def string: String
  }

  object Chunk {
    @data class Var(name: String) extends Chunk {
      def string: String = "[" + name + "]"
    }
    @data class Opt(content: Seq[Chunk]) extends Chunk {
      def string: String = "(" + content.map(_.string).mkString + ")"
    }
    object Opt {
      def apply(chunk: Chunk): Opt =
        Opt(Seq(chunk))
      def apply(chunk: Chunk, chunk1: Chunk): Opt =
        Opt(Seq(chunk, chunk1))
      def apply(chunk: Chunk, chunk1: Chunk, chunk2: Chunk): Opt =
        Opt(Seq(chunk, chunk1, chunk2))
    }
    @data class Const(value: String) extends Chunk {
      def string: String = value
    }

    implicit def fromString(s: String): Chunk = Const(s)
  }

  import Chunk.{ Var, Opt }

  // Corresponds to
  //   [organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

  val default = Pattern(
    Seq(
      Var("organisation"), "/",
      Var("module"), "/",
      Opt("scala_", Var("scalaVersion"), "/"),
      Opt("sbt_", Var("sbtVersion"), "/"),
      Var("revision"), "/",
      Var("type"), "s/",
      Var("artifact"), Opt("-", Var("classifier")), ".", Var("ext")
    )
  )

}
