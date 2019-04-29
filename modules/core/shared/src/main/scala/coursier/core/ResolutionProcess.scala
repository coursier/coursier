package coursier
package core

import coursier.util.{EitherT, Gather, Monad}

import scala.annotation.tailrec


sealed abstract class ResolutionProcess {
  def run[F[_]](
    fetch: ResolutionProcess.Fetch[F],
    maxIterations: Int = ResolutionProcess.defaultMaxIterations
  )(implicit
    F: Monad[F]
  ): F[Resolution] =
    if (maxIterations == 0) F.point(current)
    else {
      val maxIterations0 =
        if (maxIterations > 0) maxIterations - 1 else maxIterations

      this match {
        case Done(res) =>
          F.point(res)
        case missing0 @ Missing(missing, _, _) =>
          F.bind(ResolutionProcess.fetchAll[F](missing, fetch))(result =>
            missing0.next0(result).run[F](fetch, maxIterations0)
          )
        case cont @ Continue(_, _) =>
          cont
            .nextNoCont
            .run(fetch, maxIterations0)
      }
    }

  @tailrec
  final def next[F[_]](
    fetch: ResolutionProcess.Fetch[F],
    fastForward: Boolean = true
  )(implicit
    F: Monad[F]
  ): F[ResolutionProcess] =
    this match {
      case Done(_) =>
        F.point(this)
      case missing0 @ Missing(missing, _, _) =>
        F.map(ResolutionProcess.fetchAll(missing, fetch))(result => missing0.next0(result))
      case cont @ Continue(_, _) =>
        if (fastForward)
          cont.nextNoCont.next(fetch, fastForward = fastForward)
        else
          F.point(cont.next)
    }

  def current: Resolution
}

final case class Missing(
  missing: Seq[(Module, String)],
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  def next0(results: ResolutionProcess.MD): ResolutionProcess = {

    val errors = results.collect {
      case (modVer, Left(errs)) =>
        modVer -> errs
    }
    val successes = results.collect {
      case (modVer, Right(repoProj)) =>
        modVer -> repoProj
    }

    def cont0(res: Resolution): ResolutionProcess = {

      val remainingSuccesses = successes.filter {
        case (modVer, _) =>
          !res.projectCache.contains(modVer)
      }

      val depMgmtMissing0 = remainingSuccesses.map {
        case elem @ (_, (_, proj)) =>
          elem -> res.dependencyManagementMissing(proj)
      }

      val depMgmtMissing = depMgmtMissing0.map(_._2).fold(Set.empty)(_ ++ _) -- results.map(_._1)

      if (depMgmtMissing.isEmpty) {

        type Elem = ((Module, String), (Artifact.Source, Project))
        val modVer = depMgmtMissing0.map(_._1._1).toSet

        @tailrec
        def order(map: Map[Elem, Set[(Module, String)]], acc: List[Elem]): List[Elem] =
          if (map.isEmpty)
            acc.reverse
          else {
            val min = map.map(_._2.size).min // should be 0
            val (toAdd, remaining) = map.partition {
              case (_, v) => v.size == min
            }
            val acc0 = toAdd.keys.foldLeft(acc)(_.::(_))
            val remainingKeys = remaining.keySet.map(_._1)
            val map0 = remaining.map {
              case (k, v) =>
                k -> v.intersect(remainingKeys)
            }
            order(map0, acc0)
          }

        val orderedSuccesses = order(depMgmtMissing0.map { case (k, v) => k -> v.intersect(modVer) }.toMap, Nil)

        val res0 = orderedSuccesses.foldLeft(res) {
          case (acc, (modVer0, (source, proj))) =>
            acc.addToProjectCache(
              modVer0 -> (source, proj)
            )
        }

        Continue(res0, cont)
      } else
        Missing(depMgmtMissing.toSeq, res, cont0)
    }

    val current0 = current.addToErrorCache(errors)

    cont0(current0)
  }

}

final case class Continue(
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  def next: ResolutionProcess = cont(current)

  @tailrec def nextNoCont: ResolutionProcess =
    next match {
      case nextCont: Continue => nextCont.nextNoCont
      case other => other
    }

}

final case class Done(resolution: Resolution) extends ResolutionProcess {
  def current: Resolution = resolution
}

object ResolutionProcess {

  type MD = Seq[(
    (Module, String),
    Either[Seq[String], (Artifact.Source, Project)]
  )]

  type Fetch[F[_]] = Seq[(Module, String)] => F[MD]

  /**
    * Try to find `module` among `repositories`.
    *
    * Look at `repositories` from the left, one-by-one, and stop at first success.
    * Else, return all errors, in the same order.
    *
    * The `version` field of the returned `Project` in case of success may not be
    * equal to the provided one, in case the latter is not a specific
    * version (e.g. version interval). Which version get chosen depends on
    * the repository implementation.
    */
  def fetchOne[F[_]](
    repositories: Seq[Repository],
    module: Module,
    version: String,
    fetch: Repository.Fetch[F],
    fetchs: Repository.Fetch[F]*
  )(implicit
    F: Monad[F]
  ): EitherT[F, Seq[String], (Artifact.Source, Project)] = {

    def get(fetch: Repository.Fetch[F]) = {
      val lookups = repositories
        .map(repo => repo -> repo.find(module, version, fetch).run)

      val task0 = lookups.foldLeft[F[Either[Seq[String], (Artifact.Source, Project)]]](F.point(Left(Nil))) {
        case (acc, (_, eitherProjTask)) =>
          F.bind(acc) {
            case Left(errors) =>
              F.map(eitherProjTask)(_.left.map(error => error +: errors))
            case res@Right(_) =>
              F.point(res)
          }
      }

      val task = F.map(task0)(e => e.left.map(_.reverse): Either[Seq[String], (Artifact.Source, Project)])
      EitherT(task)
    }

    (get(fetch) /: fetchs)(_ orElse get(_))
  }

  def fetch[F[_]](
    repositories: Seq[core.Repository],
    fetch: Repository.Fetch[F],
    fetchs: Repository.Fetch[F]*
  )(implicit
    F: Gather[F]
  ): Fetch[F] =
    modVers =>
      F.map(
        F.gather {
          modVers.map {
            case (module, version) =>
              F.map(fetchOne(repositories, module, version, fetch, fetchs: _*).run)(d => (module, version) -> d)
          }
        }
      )(_.toSeq)



  def defaultMaxIterations: Int = 100

  def apply(resolution: Resolution): ResolutionProcess = {
    val resolution0 = resolution.nextIfNoMissing

    if (resolution0.isDone)
      Done(resolution0)
    else
      Missing(resolution0.missingFromCache.toSeq, resolution0, apply)
  }

  private[coursier] def fetchAll[F[_]](
    modVers: Seq[(Module, String)],
    fetch: ResolutionProcess.Fetch[F]
  )(implicit F: Monad[F]): F[Vector[((Module, String), Either[Seq[String], (Artifact.Source, Project)])]] = {

    def uniqueModules(modVers: Seq[(Module, String)]): Stream[Seq[(Module, String)]] = {

      val res = modVers.groupBy(_._1).toSeq.map(_._2).map {
        case Seq(v) => (v, Nil)
        case Seq() => sys.error("Cannot happen")
        case v =>
          // there might be version intervals in there, but that shouldn't matter...
          val res = v.maxBy { case (_, v0) => Version(v0) }
          (res, v.filter(_ != res))
      }

      val other = res.flatMap(_._2)

      if (other.isEmpty)
        Stream(modVers)
      else {
        val missing0 = res.map(_._1)
        missing0 #:: uniqueModules(other)
      }
    }

    uniqueModules(modVers)
      .toVector
      .foldLeft(F.point(Vector.empty[((Module, String), Either[Seq[String], (Artifact.Source, Project)])])) {
        (acc, l) =>
          F.bind(acc) { v =>
            F.map(fetch(l)) { e =>
              v ++ e
            }
          }
      }
  }

}

