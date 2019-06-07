package coursier.parse

import fastparse._, NoWhitespace._
import coursier.core.{Module, ModuleName, Organization}
import coursier.params.rule._
import coursier.util.{ModuleMatcher, ModuleMatchers}

object RuleParser {

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
    def moduleOrExcludedModule =
      P(("!".? ~ identifier).! ~ ":" ~ identifier).map {
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

    def strict =
      P("Strict" ~ ("(" ~ moduleOrExcludedModule.rep(sep = P("," ~ " ".rep)) ~ ")").?).map { modulesOpt =>
        val (exclude, include) = modulesOpt.getOrElse(Nil).partition(_.organization.value.startsWith("!"))
        val include0 = if (include.isEmpty) Set(ModuleMatcher.all) else include.map(ModuleMatcher(_)).toSet
        val exclude0 = exclude.map(m => m.copy(organization = Organization(m.organization.value.stripPrefix("!"))))
        Strict(
          include0,
          exclude0.map(ModuleMatcher(_)).toSet
        )
      }

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

  def rule(input: String): Either[String, (Rule, RuleResolution)] =
    rule(input, RuleResolution.TryResolve)

  def rule(input: String, defaultResolution: RuleResolution): Either[String, (Rule, RuleResolution)] =
    fastparse.parse(input, ruleParser(defaultResolution)(_)) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(_, idx) if idx < input.length =>
        Left(s"Unexpected characters at end of rule: '${input.drop(idx)}'")
      case Parsed.Success(rule, _) =>
        Right(rule)
    }

  def rules(input: String): Either[String, Seq[(Rule, RuleResolution)]] =
    rules(input, RuleResolution.TryResolve)

  def rules(input: String, defaultResolution: RuleResolution): Either[String, Seq[(Rule, RuleResolution)]] =
    fastparse.parse(input, rulesParser(defaultResolution)(_)) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(_, idx) if idx < input.length =>
        Left(s"Unexpected characters at end of rules: '${input.drop(idx)}'")
      case Parsed.Success(rules, idx) =>
        Right(rules)
    }

}
