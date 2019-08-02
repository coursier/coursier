package coursier.core

import scala.annotation.tailrec
import coursier.core.compatibility._

/**
 *  Used internally by Resolver.
 *
 *  Same kind of ordering as aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersion.java
 */
final case class Version(repr: String) extends Ordered[Version] {
  lazy val items = Version.items(repr)
  lazy val rawItems: Seq[Version.Item] = {
    val (first, tokens) = Version.Tokenizer(repr)
    first +: tokens.toVector.map { case (_, item) => item }
  }
  def compare(other: Version) = Version.listCompare(items, other.items)
  def isEmpty = items.forall(_.isEmpty)
}

object Version {

  private[coursier] val zero = Version("0")

  sealed abstract class Item extends Ordered[Item] {
    def compare(other: Item): Int =
      (this, other) match {
        case (Number(a), Number(b)) => a.compare(b)
        case (BigNumber(a), BigNumber(b)) => a.compare(b)
        case (Number(a), BigNumber(b)) => -b.compare(a)
        case (BigNumber(a), Number(b)) => a.compare(b)
        case (Qualifier(_, a), Qualifier(_, b)) => a.compare(b)
        case (Literal(a), Literal(b)) => a.compareToIgnoreCase(b)
        case (BuildMetadata(_), BuildMetadata(_)) =>
          // Semver § 10: two versions that differ only in the build metadata, have the same precedence.
          // Might introduce some non-determinism though :-/
          0

        case _ =>
          val rel0 = compareToEmpty
          val rel1 = other.compareToEmpty

          if (rel0 == rel1) order.compare(other.order)
          else rel0.compare(rel1)
      }

    def order: Int
    def isEmpty: Boolean = compareToEmpty == 0
    def compareToEmpty: Int = 1
  }

  sealed abstract class Numeric extends Item {
    def repr: String
    def next: Numeric
  }
  final case class Number(value: Int) extends Numeric {
    val order = 0
    def next: Number = Number(value + 1)
    def repr: String = value.toString
    override def compareToEmpty = value.compare(0)
  }
  final case class BigNumber(value: BigInt) extends Numeric {
    val order = 0
    def next: BigNumber = BigNumber(value + 1)
    def repr: String = value.toString
    override def compareToEmpty = value.compare(0)
  }
  final case class Qualifier(value: String, level: Int) extends Item {
    val order = -2
    override def compareToEmpty = level.compare(0)
  }
  final case class Literal(value: String) extends Item {
    val order = -1
    override def compareToEmpty = if (value.isEmpty) 0 else 1
  }
  final case class BuildMetadata(value: String) extends Item {
    val order = 1
    override def compareToEmpty = if (value.isEmpty) 0 else 1
  }

  case object Min extends Item {
    val order = -8
    override def compareToEmpty = -1
  }
  case object Max extends Item {
    val order = 8
  }

  val empty = Number(0)

  private val alphaQualifier = Qualifier("alpha", -5)
  private val betaQualifier = Qualifier("beta", -4)
  private val milestoneQualifier = Qualifier("milestone", -3)

  val qualifiers = Seq[Qualifier](
    alphaQualifier,
    betaQualifier,
    milestoneQualifier,
    Qualifier("cr", -2),
    Qualifier("rc", -2),
    Qualifier("snapshot", -1),
    Qualifier("ga", 0),
    Qualifier("final", 0),
    Qualifier("sp", 1)
  )

  val qualifiersMap = qualifiers.map(q => q.value -> q).toMap

  object Tokenizer {
    sealed abstract class Separator
    case object Dot extends Separator
    case object Hyphen extends Separator
    case object Underscore extends Separator
    case object Plus extends Separator
    case object None extends Separator

    def apply(s: String): (Item, Stream[(Separator, Item)]) = {
      def parseItem(s: Stream[Char]): (Item, Stream[Char]) = {
        if (s.isEmpty) (empty, s)
        else if (s.head.isDigit) {
          def digits(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
            if (s.isEmpty || !s.head.isDigit) (b.result(), s)
            else digits(b += s.head, s.tail)

          val (digits0, rem) = digits(new StringBuilder, s)
          val item =
            if (digits0.length >= 10) BigNumber(BigInt(digits0))
            else Number(digits0.toInt)

          (item, rem)
        } else if (s.head.letter) {
          def letters(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
            if (s.isEmpty || !s.head.letter)
              (b.result().toLowerCase, s) // not specifying a Locale (error with scala js)
            else
              letters(b += s.head, s.tail)

          val (letters0, rem) = letters(new StringBuilder, s)
          val item =
            qualifiersMap.getOrElse(letters0, Literal(letters0))

          (item, rem)
        } else {
          val (sep, _) = parseSeparator(s)
          if (sep == None) {
            def other(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
              if (s.isEmpty || s.head.isLetterOrDigit || parseSeparator(s)._1 != None)
                (b.result().toLowerCase, s)  // not specifying a Locale (error with scala js)
              else
                other(b += s.head, s.tail)

            val (item, rem0) = other(new StringBuilder, s)

            (Literal(item), rem0)
          } else
            (empty, s)
        }
      }

      def parseSeparator(s: Stream[Char]): (Separator, Stream[Char]) = {
        assert(s.nonEmpty)

        s.head match {
          case '.' => (Dot, s.tail)
          case '-' => (Hyphen, s.tail)
          case '_' => (Underscore, s.tail)
          case '+' => (Plus, s.tail)
          case _ => (None, s)
        }
      }

      def helper(s: Stream[Char]): Stream[(Separator, Item)] = {
        if (s.isEmpty) Stream()
        else {
          val (sep, rem0) = parseSeparator(s)
          sep match {
            case Plus =>
              Stream((sep, BuildMetadata(rem0.mkString)))
            case _ =>
              val (item, rem) = parseItem(rem0)
              (sep, item) #:: helper(rem)
          }
        }
      }

      val (first, rem) = parseItem(s.toStream)
      (first, helper(rem))
    }
  }

  def postProcess(prevIsNumeric: Option[Boolean], item: Item, tokens0: Stream[(Tokenizer.Separator, Item)]): Stream[Item] = {

    val tokens =
      // drop some '.0' under some conditions ???
      if (isNumeric(item)) {
        val nextNonDotZero = tokens0.dropWhile{case (Tokenizer.Dot, n: Numeric) => n.isEmpty; case _ => false }
        if (nextNonDotZero.headOption.forall { case (sep, t) => sep != Tokenizer.Plus && !isMinMax(t) && !isNumeric(t) })
          nextNonDotZero
        else
          tokens0
      } else
        tokens0

    def ifFollowedByNumberElse(ifFollowedByNumber: Item, default: Item) = {
      val followedByNumber = tokens.headOption
        .exists{ case (Tokenizer.None, num: Numeric) if !num.isEmpty => true; case _ => false }

      if (followedByNumber) ifFollowedByNumber
      else default
    }

    val nextItem = item match {
      case Literal("min") => Min
      case Literal("max") => Max
      case Literal("a") => ifFollowedByNumberElse(alphaQualifier, item)
      case Literal("b") => ifFollowedByNumberElse(betaQualifier, item)
      case Literal("m") => ifFollowedByNumberElse(milestoneQualifier, item)
      case _ => item
    }

    def next =
      if (tokens.isEmpty) Stream()
      else postProcess(Some(isNumeric(nextItem)), tokens.head._2, tokens.tail)

    nextItem #:: next
  }

  def isNumeric(item: Item) = item match { case _: Numeric => true; case _ => false }

  def isMinMax(item: Item) = {
    (item eq Min) || (item eq Max) || item == Literal("min") || item == Literal("max")
  }

  def items(repr: String): List[Item] = {
    val (first, tokens) = Tokenizer(repr)

    postProcess(None, first, tokens).toList
  }

  @tailrec
  def listCompare(first: List[Item], second: List[Item]): Int = {
    if (first.isEmpty && second.isEmpty) 0
    else if (first.isEmpty) {
      assert(second.nonEmpty)
      -second.dropWhile(_.isEmpty).headOption.fold(0)(_.compareToEmpty)
    } else if (second.isEmpty) {
      assert(first.nonEmpty)
      first.dropWhile(_.isEmpty).headOption.fold(0)(_.compareToEmpty)
    } else {
      val rel = first.head.compare(second.head)
      if (rel == 0) listCompare(first.tail, second.tail)
      else rel
    }
  }

}
