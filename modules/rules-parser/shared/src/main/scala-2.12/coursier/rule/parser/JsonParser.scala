package coursier.rule.parser

import argonaut.{Parse => _, _}
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import coursier.core.Module
import coursier.params.rule.{AlwaysFail, DontBumpRootDependencies, Rule, RuleResolution, SameVersion}
import coursier.util.Parse

class JsonParser(
  defaultScalaVersion: String,
  defaultRuleResolution: RuleResolution
) {

  private implicit val decodeModule: DecodeJson[Module] =
    DecodeJson { c =>
      val fromStringOpt = c.focus.string.map { s =>
        Parse.module(s, defaultScalaVersion) match {
          case Left(err) => DecodeResult.fail[Module](s"Cannot decode module '$s': $err", c.history)
          case Right(mod) => DecodeResult.ok(mod)
        }
      }

      fromStringOpt.getOrElse {
        DecodeResult.fail("Invalid module", c.history)
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
      SameVersion(r.modules.toSet)
    }
  }

  private val decodeDontBumpRootDependencies: DecodeJson[DontBumpRootDependencies.type] =
    DecodeJson { c =>
      if (c.focus.isObject)
        DecodeResult.ok(DontBumpRootDependencies)
      else
        DecodeResult.fail[DontBumpRootDependencies.type]("Expected JSON object for AlwaysFail rule", c.history)
    }

  private val ruleDecoders = Map[String, DecodeJson[Rule]](
    "always-fail" -> decodeAlwaysFail.map(x => x),
    "same-version" -> decodeSameVersion.map(x => x),
    "dont-bump-root-dependencies" -> decodeDontBumpRootDependencies.map(x => x)
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

object JsonParser {

  def parseRule(
    s: String,
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution = RuleResolution.TryResolve
  ): Either[String, (Rule, RuleResolution)] =
    new JsonParser(defaultScalaVersion, defaultRuleResolution).parseRule(s)

  def parseRules(
    s: String,
    defaultScalaVersion: String,
    defaultRuleResolution: RuleResolution = RuleResolution.TryResolve
  ): Either[String, Seq[(Rule, RuleResolution)]] =
    new JsonParser(defaultScalaVersion, defaultRuleResolution).parseRules(s)

}
