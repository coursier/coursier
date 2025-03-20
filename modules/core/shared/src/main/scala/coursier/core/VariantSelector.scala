package coursier.core

import coursier.version.{Version => Version0}
import dataclass.data

sealed abstract class VariantSelector extends Product with Serializable {
  def asConfiguration: Option[Configuration]
  def isEmpty: Boolean
  final def nonEmpty: Boolean = !isEmpty

  def repr: String
}

object VariantSelector {
  @data class ConfigurationBased(configuration: Configuration) extends VariantSelector {
    def asConfiguration: Option[Configuration] = Some(configuration)
    def isEmpty: Boolean                       = configuration.isEmpty
    def repr: String                           = configuration.value

    def equivalentAttributesSelector: Option[AttributesBased] =
      configuration match {
        case Configuration.compile | Configuration.defaultCompile =>
          val a = AttributesBased(
            Map(
              "org.gradle.category" -> VariantMatcher.Library,
              "org.gradle.usage"    -> VariantMatcher.Api
            )
          )
          Some(a)
        case Configuration.runtime | Configuration.defaultRuntime | Configuration.default =>
          val a = AttributesBased(
            Map(
              "org.gradle.category" -> VariantMatcher.Library,
              "org.gradle.usage"    -> VariantMatcher.Runtime
            )
          )
          Some(a)
        case _ => None
      }
  }

  @data class AttributesBased(
    matchers: Map[String, VariantMatcher] = Map.empty
  ) extends VariantSelector {
    def asConfiguration: Option[Configuration] = None
    def isEmpty: Boolean                       = matchers.isEmpty
    def repr: String = matchers
      .toVector
      .sortBy(_._1)
      .map {
        case (key, matcher) =>
          s"$key=${matcher.repr}"
      }
      .mkString("{ ", ", ", " }")

    def matches(variantAttributes: Map[String, String]): Option[Int] = {
      val matchesMap = matchers.map {
        case (key, matcher) =>
          key -> variantAttributes
            .get(key)
            .map(matcher.matches)
      }

      val matches0 = matchesMap.forall {
        case (_, res) =>
          res.isEmpty || res.contains(true)
      }
      def matching = matchesMap.count {
        case (_, res) =>
          res.contains(true)
      }

      if (matches0) Some(matching)
      else None
    }

    def equivalentConfiguration: Option[Configuration] = {
      val isCompile = matchers
        .get("org.gradle.usage")
        .exists { v =>
          v == VariantMatcher.Equals("api") ||
          v == VariantMatcher.Equals("java-api") ||
          v == VariantMatcher.Equals("kotlin-api") ||
          v == VariantMatcher.EndsWith("-api") ||
          v == VariantMatcher.Api
        }
      val isRuntime = matchers
        .get("org.gradle.usage")
        .exists { v =>
          v == VariantMatcher.Equals("runtime") ||
          v == VariantMatcher.Equals("java-runtime") ||
          v == VariantMatcher.Equals("kotlin-runtime") ||
          v == VariantMatcher.EndsWith("-runtime") ||
          v == VariantMatcher.Runtime
        }
      if (isCompile) Some(Configuration.compile)
      else if (isRuntime) Some(Configuration.runtime)
      else None
    }

    def addAttributes(attributes: (String, VariantSelector.VariantMatcher)*): AttributesBased =
      if (attributes.isEmpty) this
      else
        withMatchers(this.matchers ++ attributes)

    def +(other: AttributesBased): AttributesBased =
      if (isEmpty) other
      else if (other.isEmpty) this
      else withMatchers(matchers ++ other.matchers)
  }

  object AttributesBased {
    private val empty0         = AttributesBased(Map.empty)
    def empty: AttributesBased = empty0
  }

  lazy val emptyConfiguration: VariantSelector =
    VariantSelector.ConfigurationBased(Configuration.empty)

  sealed abstract class VariantMatcher extends Product with Serializable {
    def matches(value: String): Boolean
    def repr: String
  }
  object VariantMatcher {
    case object Api extends VariantMatcher {
      def matches(inputValue: String): Boolean =
        inputValue == "api" || inputValue.endsWith("-api")
      def repr: String = "api"
    }
    case object Runtime extends VariantMatcher {
      def matches(inputValue: String): Boolean =
        inputValue == "runtime" || inputValue.endsWith("-runtime")
      def repr: String = "runtime"
    }
    @data class Equals(value: String) extends VariantMatcher {
      def matches(inputValue: String): Boolean =
        inputValue == value
      def repr: String = value
    }
    @data class MinimumVersion(minimumVersion: Version0) extends VariantMatcher {
      def matches(value: String): Boolean =
        Version0(value).compareTo(minimumVersion) >= 0
      def repr: String = s">= ${minimumVersion.asString}"
    }
    @data class AnyOf(matchers: Seq[VariantMatcher]) extends VariantMatcher {
      def matches(value: String): Boolean =
        matchers.exists(_.matches(value))
      def repr: String = matchers.map(_.repr).mkString(" | ")
    }
    @data class EndsWith(suffix: String) extends VariantMatcher {
      def matches(value: String): Boolean =
        value.endsWith(suffix)
      def repr: String = s"*$suffix"
    }

    val Library: VariantMatcher = Equals("library")

    def fromString(key: String, value: String): (String, VariantMatcher) =
      if (key.endsWith("!"))
        (key.stripSuffix("!"), Equals(value))
      else {
        val matcher = key match {
          case "org.gradle.usage" =>
            if (value == "runtime") Runtime
            else if (value == "api") Api
            else Equals(value)
          case "org.gradle.jvm.version" if value.nonEmpty && value.forall(_.isDigit) =>
            MinimumVersion(Version0(value))
          case _ =>
            Equals(value)
        }
        (key, matcher)
      }
  }
}
