package coursier.rule.parser

import fastparse._
import NoWhitespace._
import coursier.core.{Module, ModuleName, Organization}
import coursier.params.rule._
import coursier.util.{ModuleMatcher, ModuleMatchers}

object ShortParser {

  private def ruleParser[_: P](defaultResolution: RuleResolution): P[(Rule, RuleResolution)] = {

    def resolution: P[RuleResolution] = {

      def tryResolve = P("resolve").map(_ => RuleResolution.TryResolve)
      def warn = P("warn").map(_ => RuleResolution.Warn)
      def fail = P("fail").map(_ => RuleResolution.Fail)

      def res = P(tryResolve | warn | fail)

      res
    }

    def alwaysFail = P("AlwaysFail").map(_ => AlwaysFail())

    def identifier =
      P(CharsWhile(c => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '.' || c == '-' || c == '_' || c == '*').!)
    def module =
      P(identifier ~ ":" ~ identifier).map {
        case (org, name) =>
          Module(Organization(org), ModuleName(name), Map.empty)
      }
    def moduleMatcher = module.map(ModuleMatcher(_))

    def excludes =
      // FIXME There should be better ways to handle whitespaces here…
      P("exclude" ~ " ".rep ~ "=" ~ " ".rep ~ "[" ~ moduleMatcher.rep(sep = P(" ".rep ~ "," ~ " ".rep)) ~ " ".rep ~ "]").map { l =>
        ModuleMatchers(l.toSet)
      }

    def includes =
    // FIXME There should be better ways to handle whitespaces here…
      P("include" ~ " ".rep ~ "=" ~ " ".rep ~ "[" ~ moduleMatcher.rep(sep = P(" ".rep ~ "," ~ " ".rep)) ~ " ".rep ~ "]").map { l =>
        ModuleMatchers(Set(), l.toSet)
      }

    def sameVersion =
      P("SameVersion(" ~ module.rep(min = 1, sep = P("," ~ " ".rep)) ~ ")").map { modules =>
          SameVersion(modules.map(ModuleMatcher(_)).toSet)
      }

    def dontBumpRootDependencies =
      P("DontBumpRootDependencies" ~ ("(" ~ (excludes | includes).rep(sep = P("," ~ " ".rep)) ~ ")").?).map { l =>
        val matchers = l.getOrElse(Nil).foldLeft(ModuleMatchers.all) {
          (m, m0) =>
            ModuleMatchers(m.exclude ++ m0.exclude, m.include ++ m0.include)
        }
        DontBumpRootDependencies(matchers)
      }

    def strict = P("Strict").map(_ => Strict)

    def rule =
      P((resolution ~ ":").? ~ (alwaysFail | sameVersion | dontBumpRootDependencies | strict)).map {
        case (resOpt, rule) =>
          rule -> resOpt.getOrElse(defaultResolution)
      }

    rule
  }

  private def rulesParser[_: P](defaultResolution: RuleResolution): P[Seq[(Rule, RuleResolution)]] = {

    def rules = P(ruleParser(defaultResolution).rep(min = 1, sep = P("," ~ " ".rep)))

    rules
  }

  def parseRule(s: String, defaultResolution: RuleResolution = RuleResolution.TryResolve): Either[String, (Rule, RuleResolution)] =
    fastparse.parse(s, ruleParser(defaultResolution)(_)) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(_, idx) if idx < s.length =>
        Left(s"Unexpected characters at end of rule: '${s.drop(idx)}'")
      case Parsed.Success(rule, _) =>
        Right(rule)
    }

  def parseRules(s: String, defaultResolution: RuleResolution = RuleResolution.TryResolve): Either[String, Seq[(Rule, RuleResolution)]] =
    fastparse.parse(s, rulesParser(defaultResolution)(_)) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(_, idx) if idx < s.length =>
        Left(s"Unexpected characters at end of rules: '${s.drop(idx)}'")
      case Parsed.Success(rules, idx) =>
        Right(rules)
    }

}
