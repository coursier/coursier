package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.{
  JsonReaderException,
  JsonValueCodec,
  readFromString
}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import scala.annotation.tailrec
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ListBuffer

object DockerFile {

  final case class WithLines[+T](value: T, lines: SortedSet[Int]) {
    def prependLines(toPrepend: Iterable[Int]): WithLines[T] =
      copy(lines = lines ++ toPrepend)
    def map[U](f: T => U): WithLines[U] =
      copy(value = f(value))
    def flatMap[U](f: T => WithLines[U]): WithLines[U] =
      f(value)
        .prependLines(lines)
  }

  private val splitInstruction = "\\s+".r

  final class ParseException(lines: Seq[Int], error: String, cause: Throwable = null)
      extends Exception(
        {
          assert(lines.nonEmpty)
          val linesMsg =
            if (lines.lengthCompare(1) == 0) s"Line ${lines.head}"
            else s"Lines ${lines.mkString(", ")}"
          s"$linesMsg: $error"
        },
        cause
      )

  private val stringListJsonCodec: JsonValueCodec[Seq[String]] =
    JsonCodecMaker.make

  def parse(lines: Iterator[WithLines[String]])
    : Either[ParseException, Seq[WithLines[DockerInstruction]]] = {

    val it              = lines.filter(!_.value.trim.startsWith("#"))
    val rawInstructions = new ListBuffer[WithLines[(String, String)]]

    while (it.hasNext) {
      val line = it.next().map(_.trim())
      if (line.value.nonEmpty) {
        val rawInstruction = line.flatMap { line0 =>
          val (instructionName, rest) = splitInstruction.pattern.split(line0, 2) match {
            case Array(instructionName0) =>
              (instructionName0, "")
            case Array(instructionName0, rest0) =>
              (instructionName0, rest0)
          }

          val fullRest =
            if (!rest.startsWith("[") && rest.endsWith("\\")) {
              var wrapper = WithLines((), SortedSet.empty)
              val b       = new StringBuffer
              b.append(rest, 0, rest.length - 1)
              var keepReading = true
              while (keepReading && it.hasNext) {
                val extraLine = it.next()
                wrapper = wrapper.flatMap(_ => extraLine.map(_ => ()))
                val extraLine0 = extraLine.value
                keepReading = extraLine0.endsWith("\\")
                if (keepReading)
                  b.append(extraLine0, 0, extraLine0.length - 1)
                else
                  b.append(extraLine0)
              }
              wrapper.map(_ => b.toString.trim)
            }
            else
              WithLines(rest.trim, SortedSet.empty)

          fullRest.map((instructionName, _))
        }
        rawInstructions += rawInstruction
      }
    }

    def parseSingleArg(rest: WithLines[String]): Either[ParseException, WithLines[String]] =
      if (splitInstruction.findFirstIn(rest.value).isDefined)
        Left(new ParseException(rest.lines.toSeq, "Expected single argument, got several"))
      else
        Right(rest)

    def parseTwoArgs(rest: WithLines[String]): Either[ParseException, WithLines[(String, String)]] =
      splitInstruction.split(rest.value) match {
        case Array(one) =>
          Left(new ParseException(rest.lines.toSeq, "Expected two arguments, found only one"))
        case Array(first, second) =>
          Right(rest.map(_ => (first, second)))
        case other =>
          Left(new ParseException(
            rest.lines.toSeq,
            s"Expected two arguments, found ${other.length}"
          ))
      }

    def parseAtLeastOneArg(rest: WithLines[String])
      : Either[ParseException, WithLines[Seq[String]]] =
      if (rest.value.startsWith("["))
        try Right(rest.map(readFromString(_)(stringListJsonCodec)))
        catch {
          case ex: JsonReaderException =>
            Left(new ParseException(rest.lines.toSeq, "Error parsing JSON array", ex))
        }
      else if (rest.value.isEmpty)
        Left(new ParseException(rest.lines.toSeq, "Expected at least one argument"))
      else
        Right(rest.map(splitInstruction.split(_).toSeq))

    var errorOpt     = Option.empty[ParseException]
    val instructions = new ListBuffer[WithLines[DockerInstruction]]
    val rawInstIt    = rawInstructions.iterator
    while (rawInstIt.hasNext && errorOpt.isEmpty) {
      val rawInst = rawInstIt.next()
      rawInst.value._1 match {
        case "FROM" =>
          parseSingleArg(rawInst.map(_._2)) match {
            case Left(err) =>
              errorOpt = Some(err)
            case Right(arg) =>
              instructions += arg.map(arg0 => DockerInstruction.From(arg0))
          }
        case "CMD" =>
          parseAtLeastOneArg(rawInst.map(_._2)) match {
            case Left(err) =>
              errorOpt = Some(err)
            case Right(args) =>
              instructions += args.map(args0 => DockerInstruction.Cmd(args0))
          }
        case "COPY" =>
          parseTwoArgs(rawInst.map(_._2)) match {
            case Left(err) =>
              errorOpt = Some(err)
            case Right(args) =>
              instructions += args.map {
                case (first, second) =>
                  DockerInstruction.Copy(first, second)
              }
          }
        case "WORKDIR" =>
          parseSingleArg(rawInst.map(_._2)) match {
            case Left(err) =>
              errorOpt = Some(err)
            case Right(arg) =>
              instructions += arg.map(arg0 => DockerInstruction.WorkDir(arg0))
          }
        case "EXPOSE" =>
          parseSingleArg(rawInst.map(_._2)) match {
            case Left(err) =>
              errorOpt = Some(err)
            case Right(arg) =>
              try {
                val port = arg.value.toInt
                instructions += arg.map(arg0 => DockerInstruction.Expose(port))
              }
              catch {
                case e: NumberFormatException =>
                  errorOpt =
                    Some(new ParseException(rawInst.lines.toSeq, "Invalid value for port", e))
              }
          }
        case "RUN" =>
          parseAtLeastOneArg(rawInst.map(_._2)) match {
            case Left(err) =>
              errorOpt = Some(err)
            case Right(args) =>
              instructions += args.map(args0 => DockerInstruction.Run(args0))
          }
        case other =>
          errorOpt = Some(
            new ParseException(
              rawInst.lines.toSeq,
              s"Unrecognized Dockerfile instruction: '$other'"
            )
          )
      }
    }

    errorOpt.toLeft(instructions.toList)
  }

  def validateShape(instructions: Seq[WithLines[DockerInstruction]]): Either[
    ParseException,
    (WithLines[DockerInstruction.From], Seq[WithLines[DockerInstruction.NonHead]])
  ] =
    instructions match {
      case Seq() =>
        Left(
          new ParseException(
            Seq(0),
            "Expected a FROM instruction at the top of the file or after initial comments or empty lines"
          )
        )
      case Seq(first, others @ _*) =>
        val maybeFrom = first.value match {
          case from: DockerInstruction.From => Right(first.map(_ => from))
          case other =>
            Left(new ParseException(first.lines.toSeq, "Expected a FROM instruction upfront"))
        }
        val maybeOthers = {
          val notOk = others.collect {
            case elem if elem.value.asNonHead.isEmpty => elem
          }
          if (notOk.isEmpty)
            Right(
              others.flatMap { elem =>
                elem.value.asNonHead.map(elem0 => elem.map(_ => elem0))
              }
            )
          else
            Left(
              new ParseException(notOk.flatMap(_.lines).distinct, "Found unexpected instructions")
            )
        }
        (maybeFrom, maybeOthers) match {
          case (Right(from), Right(others)) =>
            Right((from, others))
          case (Left(fromErr), Right(_)) =>
            Left(fromErr)
          case (Right(_), Left(otherErr)) =>
            Left(otherErr)
          case (Left(fromErr), Left(otherErr)) =>
            fromErr.addSuppressed(otherErr)
            Left(fromErr)
        }
    }

}
