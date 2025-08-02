package coursier.parse

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import coursier.core.Module
import coursier.params.rule._
import coursier.util.{ModuleMatcher, ModuleMatchers}

import java.nio.charset.StandardCharsets

import scala.collection.mutable

class JsonRuleParser(
  defaultScalaVersion: String,
  defaultRuleResolution: RuleResolution
) {

  private implicit val moduleCodec: JsonValueCodec[Module] = {
    val strCodec = JsonCodecMaker.make[String]
    strCodec.xmapE[Module](
      (str, in) =>
        ModuleParser.module(str, defaultScalaVersion) match {
          case Left(err)  => in.decodeError(s"Cannot decode module '$str': $err")
          case Right(mod) => mod
        },
      _.repr,
      _ => null
    )
  }

  private val moduleMatchersCodec: JsonValueCodec[ModuleMatchers] = {

    final case class Helper(
      exclude: List[Module] = Nil,
      include: List[Module] = Nil
    )

    val codec = JsonCodecMaker.make[Helper]

    codec.xmap[ModuleMatchers](
      helper =>
        ModuleMatchers(
          helper.exclude.map(ModuleMatcher(_)).toSet,
          helper.include.map(ModuleMatcher(_)).toSet
        ),
      // This should be unused… We should sort things right after the toList calls if this ends up being used.
      matchers =>
        Helper(matchers.exclude.toList.map(_.matcher), matchers.include.toList.map(_.matcher)),
      _ => ModuleMatchers(Set.empty)
    )
  }

  private implicit class JsonValueCodecOps[T](private val codec: JsonValueCodec[T]) {
    def xmap[U](from: T => U, to: U => T, nullValue: T => U): JsonValueCodec[U] = {
      val nullValue0 = nullValue
      new JsonValueCodec[U] {
        def decodeValue(in: JsonReader, default: U) =
          from(codec.decodeValue(in, codec.nullValue))
        def encodeValue(x: U, out: JsonWriter) =
          codec.encodeValue(to(x), out)
        def nullValue =
          nullValue0(codec.nullValue)
      }
    }
    def xmapE[U](from: (T, JsonReader) => U, to: U => T, nullValue: T => U): JsonValueCodec[U] = {
      val nullValue0 = nullValue
      new JsonValueCodec[U] {
        def decodeValue(in: JsonReader, default: U) =
          from(codec.decodeValue(in, codec.nullValue), in)
        def encodeValue(x: U, out: JsonWriter) =
          codec.encodeValue(to(x), out)
        def nullValue =
          nullValue0(codec.nullValue)
      }
    }
  }

  private val alwaysFailCodec: JsonValueCodec[AlwaysFail] = {
    final case class Helper()
    val codec = JsonCodecMaker.make[Helper]
    codec.xmap(
      _ => AlwaysFail(),
      _ => Helper(),
      _ => AlwaysFail()
    )
  }

  private val sameVersionCodec: JsonValueCodec[SameVersion] = {
    final case class Repr(modules: List[Module])
    val codec = JsonCodecMaker.make[Repr]
    codec.xmap(
      repr => SameVersion(repr.modules.map(ModuleMatcher(_)).toSet),
      // should be unused… we should probably sort modules if this gets used:
      sv => Repr(sv.matchers.toList.map(_.matcher)),
      _ => SameVersion(Set.empty[ModuleMatcher])
    )
  }

  private val dontBumpRootDependenciesCodec: JsonValueCodec[DontBumpRootDependencies] =
    moduleMatchersCodec.xmap[DontBumpRootDependencies](
      DontBumpRootDependencies(_),
      _.matchers,
      _ => DontBumpRootDependencies()
    )

  private val strictCodec: JsonValueCodec[Strict] = {

    final case class Repr(include: List[Module] = Nil, exclude: List[Module] = Nil)
    val codec = JsonCodecMaker.make[Repr]

    codec.xmap[Strict](
      repr => {
        val include =
          if (repr.include.isEmpty) Set(ModuleMatcher.all)
          else repr.include.map(ModuleMatcher(_)).toSet
        Strict(
          include,
          repr.exclude.map(ModuleMatcher(_)).toSet
        )
      },
      strict => {
        val include =
          if (strict.include == Set(ModuleMatcher.all)) Nil
          else strict.include.toList.map(_.matcher)
        Repr(
          include,
          strict.exclude.toList.map(_.matcher)
        )
      },
      _ => Strict()
    )
  }

  private val ruleCodecs = Map[String, JsonValueCodec[_ <: Rule]](
    "always-fail"                 -> alwaysFailCodec,
    "same-version"                -> sameVersionCodec,
    "dont-bump-root-dependencies" -> dontBumpRootDependenciesCodec,
    "strict"                      -> strictCodec
  )

  private def decodeRuleResolution(ruleBytes: Array[Byte]): Option[RuleResolution] = {

    // FIXME We're ignoring malformed "action" fields here

    final case class Helper(action: Option[String] = None)
    val codec = JsonCodecMaker.make[Helper]

    val helper = readFromArray(ruleBytes)(codec)
    helper.action.collect {
      case "fail"        => RuleResolution.Fail
      case "warn"        => RuleResolution.Warn
      case "try-resolve" => RuleResolution.TryResolve
    }
  }

  private def decodeRuleName(ruleBytes: Array[Byte]): Option[String] = {

    // FIXME We're ignoring malformed "action" fields here

    final case class Helper(rule: Option[String] = None)
    val codec = JsonCodecMaker.make[Helper]

    val helper = readFromArray(ruleBytes)(codec)
    helper.rule
  }

  def parseRule(s: String): Either[String, (Rule, RuleResolution)] = {
    val b = s.getBytes(StandardCharsets.UTF_8)
    parseRule(b)
  }

  def parseRule(b: Array[Byte]): Either[String, (Rule, RuleResolution)] = {
    val ruleResOpt  = decodeRuleResolution(b)
    val ruleNameOpt = decodeRuleName(b)

    ruleNameOpt match {
      case None       => Left("No rule name found")
      case Some(name) =>
        ruleCodecs.get(name) match {
          case None => Left(
              s"Rule '$name' not found (available rules: ${ruleCodecs.keys.toVector.sorted.mkString(", ")})"
            )
          case Some(ruleCodec) =>
            val rule    = readFromArray(b)(ruleCodec)
            val ruleRes = ruleResOpt.getOrElse(defaultRuleResolution)
            Right((rule, ruleRes))
        }
    }
  }

  def parseRules(s: String): Either[String, Seq[(Rule, RuleResolution)]] = {
    val b = s.getBytes(StandardCharsets.UTF_8)
    parseRules(b)
  }

  def parseRules(b: Array[Byte]): Either[String, Seq[(Rule, RuleResolution)]] = {

    val codec = JsonCodecMaker.make[List[RawJson]]

    val elems = readFromArray(b)(codec)

    val b0     = new mutable.ListBuffer[(Rule, RuleResolution)]
    var errOpt = Option.empty[String]
    val it     = elems.iterator
    while (it.hasNext && errOpt.isEmpty) {
      val rawJson = it.next()
      parseRule(rawJson.value) match {
        case Left(err) =>
          errOpt = Some(err)
        case Right(value) =>
          b0 += value
      }
    }

    errOpt.toLeft(b0.result())
  }

}

object JsonRuleParser {

  def parseRule(
    s: String,
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution = RuleResolution.TryResolve
  ): Either[String, (Rule, RuleResolution)] =
    new JsonRuleParser(defaultScalaVersion, defaultRuleResolution).parseRule(s)

  def parseRule(
    content: Array[Byte],
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution
  ): Either[String, (Rule, RuleResolution)] =
    new JsonRuleParser(defaultScalaVersion, defaultRuleResolution).parseRule(content)

  def parseRule(
    content: Array[Byte],
    defaultScalaVersion: String
  ): Either[String, (Rule, RuleResolution)] =
    new JsonRuleParser(defaultScalaVersion, RuleResolution.TryResolve).parseRule(content)

  def parseRules(
    s: String,
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution = RuleResolution.TryResolve
  ): Either[String, Seq[(Rule, RuleResolution)]] =
    new JsonRuleParser(defaultScalaVersion, defaultRuleResolution).parseRules(s)

  def parseRules(
    content: Array[Byte],
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution
  ): Either[String, Seq[(Rule, RuleResolution)]] =
    new JsonRuleParser(defaultScalaVersion, defaultRuleResolution).parseRules(content)

  def parseRules(
    content: Array[Byte],
    defaultScalaVersion: String
  ): Either[String, Seq[(Rule, RuleResolution)]] =
    new JsonRuleParser(defaultScalaVersion, RuleResolution.TryResolve).parseRules(content)

}
