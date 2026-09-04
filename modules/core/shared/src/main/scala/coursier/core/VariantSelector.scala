package coursier.core

import coursier.version.{Version => Version0}
import dataclass.data

import scala.annotation.tailrec

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
    def repr: String                           = matchers
      .toVector
      .sortBy(_._1)
      .map {
        case (key, matcher) =>
          s"$key=${matcher.repr}"
      }
      .mkString("{ ", ", ", " }")
    override def toString: String =
      if (AttributesBased.reprAsToString.get())
        repr
      else
        s"AttributesBased$tuple"

    def matches(variantAttributes: Map[String, String]): Option[(Int, Int)] = {
      val matchesMap = matchers.map {
        case (key, matcher) =>
          key -> variantAttributes
            .get(key)
            .map(matcher.matches)
      }

      val matches0 = matchesMap.forall {
        case (_, res) =>
          res.isEmpty || res.exists(_.nonEmpty)
      }

      if (matches0) {
        val matching = matchesMap.count {
          case (_, res) =>
            res.nonEmpty
        }
        val subScore = matchesMap
          .collect {
            case (_, Some(Some(n))) =>
              n
          }
          .sum
        Some((matching, subScore))
      }
      else None
    }

    def equivalentConfiguration: Option[Configuration] =
      if (matchers.get("org.gradle.category").contains(VariantMatcher.Library)) {
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
      else if (matchers.get("org.gradle.category").contains(VariantMatcher.Platform))
        Some(Configuration.`import`)
      else
        None

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

    private[coursier] val reprAsToString: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
      override protected def initialValue(): Boolean =
        false
    }

    def sources: AttributesBased =
      AttributesBased().addAttributes(
        // seems sources are put in runtime scope in Gradle Modules
        "org.gradle.usage"    -> VariantMatcher.Runtime,
        "org.gradle.category" -> VariantMatcher.Documentation,
        "org.gradle.docstype" ->
          VariantMatcher.AnyOf(Seq(
            VariantMatcher.Equals("sources"),
            VariantMatcher.Equals("fake-sources")
          ))
      )
  }

  lazy val emptyConfiguration: VariantSelector =
    VariantSelector.ConfigurationBased(Configuration.empty)

  sealed abstract class VariantMatcher extends Product with Serializable {
    def matches(value: String): Option[Int]
    def repr: String
  }
  object VariantMatcher {
    case object Api extends VariantMatcher {
      def matches(inputValue: String): Option[Int] =
        if (inputValue == "api" || inputValue.endsWith("-api")) Some(0)
        else None
      def repr: String = "api"
    }
    case object Runtime extends VariantMatcher {
      def matches(inputValue: String): Option[Int] =
        if (inputValue == "runtime" || inputValue.endsWith("-runtime")) Some(0)
        else None
      def repr: String = "runtime"
    }
    @data class Equals(value: String) extends VariantMatcher {
      def matches(inputValue: String): Option[Int] =
        if (inputValue == value) Some(0)
        else None
      def repr: String = value
    }
    @data class MinimumVersion(minimumVersion: Version0) extends VariantMatcher {
      def matches(value: String): Option[Int] =
        if (Version0(value).compareTo(minimumVersion) >= 0) Some(0)
        else None
      def repr: String = s">= ${minimumVersion.asString}"
    }
    @data class AnyOf(matchers: Seq[VariantMatcher]) extends VariantMatcher {
      def matches(value: String): Option[Int] =
        matchers
          .iterator
          .zipWithIndex
          .collectFirst {
            case (matcher, idx) if matcher.matches(value).nonEmpty =>
              matchers.length - 1 - idx
          }
      def repr: String = matchers.map(_.repr).mkString(" | ")
    }
    @data class EndsWith(suffix: String) extends VariantMatcher {
      def matches(value: String): Option[Int] =
        if (value.endsWith(suffix)) Some(0)
        else None
      def repr: String = s"*$suffix"
    }

    val Library: VariantMatcher       = Equals("library")
    val Platform: VariantMatcher      = Equals("platform")
    val Documentation: VariantMatcher = Equals("documentation")

    def fromString(key: String, value: String): (String, VariantMatcher) =
      if (key.endsWith("!"))
        (key.stripSuffix("!"), Equals(value))
      else {
        val valueMaker: String => VariantMatcher =
          key match {
            case "org.gradle.usage" =>
              value =>
                if (value == "runtime") Runtime
                else if (value == "api") Api
                else Equals(value)
            case "org.gradle.jvm.version" =>
              value =>
                if (value.nonEmpty && value.forall(_.isDigit)) MinimumVersion(Version0(value))
                else Equals(value)
            case _ =>
              value =>
                Equals(value)
          }

        def splitValue(value: String): Option[(String, String)] = {
          var idx     = 0
          var splitAt = -1
          while (splitAt < 0 && idx < value.length)
            if (value(idx) == '\\')
              idx += 2
            else if (value(idx) == '|')
              splitAt = idx
            else
              idx += 1
          if (splitAt < 0) None
          else Some((value.take(splitAt), value.drop(splitAt + 1)))
        }

        @tailrec
        def splitted(acc: List[String], value: String): List[String] =
          splitValue(value) match {
            case None =>
              (value :: acc).reverse
            case Some((first, remaining)) =>
              splitted(first :: acc, remaining)
          }

        val matcher = splitted(Nil, value).map(valueMaker) match {
          case Nil            => sys.error("Cannot happen")
          case matcher :: Nil => matcher
          case matchers       => AnyOf(matchers)
        }

        (key, matcher)
      }
  }
}
