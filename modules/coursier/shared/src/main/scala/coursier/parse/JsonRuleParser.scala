package coursier.parse

import argonaut.{Parse => _, _}
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import coursier.core.Module
import coursier.params.rule._
import coursier.util.{ModuleMatcher, ModuleMatchers}

class JsonRuleParser(
  defaultScalaVersion: String,
  defaultRuleResolution: RuleResolution
) {

  private implicit val decodeModule: DecodeJson[Module] =
    DecodeJson { c =>
      val fromStringOpt = c.focus.string.map { s =>
        ModuleParser.module(s, defaultScalaVersion) match {
          case Left(err) => DecodeResult.fail[Module](s"Cannot decode module '$s': $err", c.history)
          case Right(mod) => DecodeResult.ok(mod)
        }
      }

      fromStringOpt.getOrElse {
        DecodeResult.fail("Invalid module", c.history)
      }
    }

  private implicit val decodeModuleMatcher: DecodeJson[ModuleMatcher] =
    decodeModule.map(ModuleMatcher(_))

  private implicit val decodeModuleMatchers: DecodeJson[ModuleMatchers] =
    DecodeJson {

      final case class Helper(exclude: List[ModuleMatcher] = Nil, include: List[ModuleMatcher] = Nil)

      val decodeHelper = DecodeJson.of[Helper]

      c =>
        decodeHelper(c).map { h =>
          ModuleMatchers(h.exclude.toSet, h.include.toSet)
        }
    }

  private val decodeAlwaysFail: DecodeJson[AlwaysFail] =
    DecodeJson { c =>
      if (c.focus.isObject)
        DecodeResult.ok(AlwaysFail())
      else
        DecodeResult.fail[AlwaysFail]("Expected JSON object for AlwaysFail rule", c.history)
    }

  private val decodeSameVersion: DecodeJson[SameVersion] = {

    final case class Repr(modules: List[Module])

    DecodeJson.of[Repr].map { r =>
      SameVersion(r.modules.map(ModuleMatcher(_)).toSet)
    }
  }

  private val decodeDontBumpRootDependencies: DecodeJson[DontBumpRootDependencies] =
    DecodeJson { c =>
      if (c.focus.isObject)
        decodeModuleMatchers(c).map { m =>
          DontBumpRootDependencies(m)
        }
      else
        DecodeResult.fail[DontBumpRootDependencies]("Expected JSON object for AlwaysFail rule", c.history)
    }

  private val decodeStrict: DecodeJson[Strict] = {

    final case class Repr(include: List[Module] = Nil, exclude: List[Module] = Nil)

    DecodeJson.of[Repr].map { r =>
      val include = if (r.include.isEmpty) Set(ModuleMatcher.all) else r.include.map(ModuleMatcher(_)).toSet
      Strict(
        include,
        r.exclude.map(ModuleMatcher(_)).toSet
      )
    }
  }

  private val ruleDecoders = Map[String, DecodeJson[Rule]](
    "always-fail" -> decodeAlwaysFail.map(x => x),
    "same-version" -> decodeSameVersion.map(x => x),
    "dont-bump-root-dependencies" -> decodeDontBumpRootDependencies.map(x => x),
    "strict" -> decodeStrict.map(x => x)
  )

  private val decodeRule: DecodeJson[(Rule, RuleResolution)] =
    DecodeJson { c =>

      val hasAction = c.fieldSet.exists(_.contains("action"))
      val ruleResCursor = c.downField("action")

      // FIXME We're ignoring malformed "action" fields here
      val ruleResOpt = ruleResCursor
        .focus
        .flatMap(_.string)
        .collect {
          case "fail" => RuleResolution.Fail
          case "warn" => RuleResolution.Warn
          case "try-resolve" => RuleResolution.TryResolve
        }

      val ruleCursor = (if (hasAction) ruleResCursor.deleteGoParent else c.acursor).downField("rule")

      ruleCursor.focus.flatMap(_.string) match {
        case None =>
          DecodeResult.fail[(Rule, RuleResolution)]("No rule name found", c.history)
        case Some(name) =>
          ruleDecoders.get(name) match {
            case None =>
              DecodeResult.fail[(Rule, RuleResolution)](
                s"Rule '$name' not found (available rules: ${ruleDecoders.keys.toVector.sorted.mkString(", ")})",
                c.history
              )
            case Some(ruleDecoder) =>
              ruleDecoder.tryDecode(ruleCursor.deleteGoParent).map { rule =>
                (rule, ruleResOpt.getOrElse(defaultRuleResolution))
              }
          }
      }
    }

  def parseRule(s: String): Either[String, (Rule, RuleResolution)] =
    s.decodeEither(decodeRule)

  def parseRules(s: String): Either[String, Seq[(Rule, RuleResolution)]] =
    s.decodeEither(DecodeJson.ListDecodeJson(decodeRule))

}

object JsonRuleParser {

  def parseRule(
    s: String,
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution = RuleResolution.TryResolve
  ): Either[String, (Rule, RuleResolution)] =
    new JsonRuleParser(defaultScalaVersion, defaultRuleResolution).parseRule(s)

  def parseRules(
    s: String,
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution = RuleResolution.TryResolve
  ): Either[String, Seq[(Rule, RuleResolution)]] =
    new JsonRuleParser(defaultScalaVersion, defaultRuleResolution).parseRules(s)

}
