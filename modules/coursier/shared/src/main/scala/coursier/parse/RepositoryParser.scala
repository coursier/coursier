package coursier.parse

import coursier.core.Repository
import coursier.internal.PlatformRepositoryParser
import coursier.util.ValidationNel
import coursier.util.Traverse.TraverseOps

object RepositoryParser extends PlatformRepositoryParser {

  private def repositories0(
    inputs: Seq[String],
    defaultRepositoriesOpt: Option[Seq[Repository]]
  ): ValidationNel[String, Seq[Repository]] =
    defaultRepositoriesOpt match {
      case Some(defaultRepositories) =>
        inputs
          .toVector
          .validationNelTraverse[String, Seq[Repository]] {
            case "default" =>
              ValidationNel.success(defaultRepositories)
            case s =>
              ValidationNel.fromEither(repository(s).map(Seq(_)))
          }
          .map(_.flatten)
      case None =>
        inputs
          .toVector
          .validationNelTraverse[String, Repository] {
            case s =>
              ValidationNel.fromEither(repository(s))
          }
    }

  /** Parses repository strings, accepting "default" as a value too
    *
    * When "default" is passed in one of the input values, `defaultRepositories` is used for it.
    *
    * @param inputs
    *   input strings to parse
    * @param defaultRepositories
    *   repositories to use as default
    * @return
    *   Parsed repositories, or one or more errors
    */
  def repositories(
    inputs: Seq[String],
    defaultRepositories: Seq[Repository]
  ): ValidationNel[String, Seq[Repository]] =
    repositories0(inputs, Some(defaultRepositories))

  /** Parses repository strings
    *
    * Unlike the override accepting default repositories, this one doesn't accept "default" as
    * value.
    *
    * @param inputs
    *   input strings to parse
    * @return
    *   Parsed repositories, or one or more errors
    */
  def repositories(inputs: Seq[String]): ValidationNel[String, Seq[Repository]] =
    repositories0(inputs, None)

}
