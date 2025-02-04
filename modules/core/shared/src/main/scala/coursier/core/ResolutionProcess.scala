package coursier.core

import coursier.version.{VersionConstraint => VersionConstraint0}
import coursier.util.{EitherT, Gather, Monad}
import coursier.util.Monad.ops._
import dataclass.data

import scala.annotation.tailrec
import scala.collection.compat.immutable.LazyList

sealed abstract class ResolutionProcess extends Product with Serializable {
  def run[F[_]](
    fetch: ResolutionProcess.Fetch0[F],
    maxIterations: Int = ResolutionProcess.defaultMaxIterations
  )(implicit
    F: Monad[F]
  ): F[Resolution] =
    if (maxIterations == 0) F.point(current)
    else {
      val maxIterations0 =
        if (maxIterations > 0) maxIterations - 1 else maxIterations

      this match {
        case done: Done =>
          F.point(done.resolution)
        case missing0: Missing =>
          ResolutionProcess.fetchAll[F](missing0.missing0, fetch).flatMap(result =>
            missing0.next0_(result).run[F](fetch, maxIterations0)
          )
        case cont: Continue =>
          cont
            .nextNoCont
            .run(fetch, maxIterations0)
      }
    }

  @tailrec
  final def next[F[_]](
    fetch: ResolutionProcess.Fetch0[F],
    fastForward: Boolean = true
  )(implicit
    F: Monad[F]
  ): F[ResolutionProcess] =
    this match {
      case _: Done =>
        F.point(this)
      case missing0: Missing =>
        ResolutionProcess.fetchAll(missing0.missing0, fetch)
          .map(result => missing0.next0_(result))
      case cont: Continue =>
        if (fastForward)
          cont.nextNoCont.next(fetch, fastForward = fastForward)
        else
          F.point(cont.next)
    }

  def current: Resolution
}

@data class Missing(
  missing0: Seq[(Module, VersionConstraint0)],
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  @deprecated("Use missing0 instead", "2.1.25")
  def missing: Seq[(Module, String)] =
    missing0.map {
      case (mod, ver) =>
        (mod, ver.asString)
    }
  @deprecated("Use missing0 instead", "2.1.25")
  def withMissing(newMissing: Seq[(Module, String)]): Missing =
    withMissing0(
      newMissing.map {
        case (mod, ver) =>
          (mod, VersionConstraint0(ver))
      }
    )

  @deprecated("Use next0_ instead", "2.1.25")
  def next0(results: ResolutionProcess.MD): ResolutionProcess =
    next0_(
      results.map {
        case ((mod, ver), thing) =>
          ((mod, VersionConstraint0(ver)), thing)
      }
    )

  def next0_(results: ResolutionProcess.MD0): ResolutionProcess = {

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
          !res.projectCache0.contains(modVer)
      }

      val depMgmtMissing0 = remainingSuccesses.map {
        case elem @ (_, (_, proj)) =>
          elem -> res.dependencyManagementMissing0(proj)
      }

      val depMgmtMissing = depMgmtMissing0.map(_._2).fold(Set.empty)(_ ++ _)

      if (depMgmtMissing.isEmpty) {

        type Elem = ((Module, VersionConstraint0), (ArtifactSource, Project))
        val modVer = depMgmtMissing0.map(_._1._1).toSet

        @tailrec
        def order(map: Map[Elem, Set[(Module, VersionConstraint0)]], acc: List[Elem]): List[Elem] =
          if (map.isEmpty)
            acc.reverse
          else {
            val min = map.map(_._2.size).min // should be 0
            val (toAdd, remaining) = map.partition {
              case (_, v) => v.size == min
            }
            val acc0                                             = toAdd.keys.foldLeft(acc)(_.::(_))
            val remainingKeys: Set[(Module, VersionConstraint0)] = remaining.keySet.map(_._1)
            val map0 = remaining.map {
              case (k, v) =>
                k -> v.intersect(remainingKeys)
            }
            order(map0, acc0)
          }

        val orderedSuccesses = order(
          depMgmtMissing0.map { case (k, v) => k -> v.intersect(modVer) }.toMap,
          Nil
        )

        val res0 = orderedSuccesses.foldLeft(res) {
          case (acc, (modVer0, (source, proj))) =>
            acc.addToProjectCache0(
              modVer0 -> (source, proj)
            )
        }

        Continue(res0, cont)
      }
      else
        Missing(depMgmtMissing.toSeq, res, cont0)
    }

    val current0 = current.addToErrorCache0(errors)

    cont0(current0)
  }

}

@data class Continue(
  current: Resolution,
  cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  def next: ResolutionProcess = cont(current)

  @tailrec def nextNoCont: ResolutionProcess =
    next match {
      case nextCont: Continue => nextCont.nextNoCont
      case other              => other
    }

}

@data class Done(resolution: Resolution) extends ResolutionProcess {

  def current: Resolution = resolution
}

object ResolutionProcess {

  @deprecated("Use ResolutionProcess.MD0 instead", "2.1.25")
  type MD = Seq[(
    (Module, String),
    Either[Seq[String], (ArtifactSource, Project)]
  )]

  @deprecated("Use ResolutionProcess.Fetch0 instead", "2.1.25")
  type Fetch[F[_]] = Seq[(Module, String)] => F[MD]

  type MD0 = Seq[(
    (Module, VersionConstraint0),
    Either[Seq[String], (ArtifactSource, Project)]
  )]

  type Fetch0[F[_]] = Seq[(Module, VersionConstraint0)] => F[MD0]

  /** Try to find `module` among `repositories`.
    *
    * Look at `repositories` from the left, one-by-one, and stop at first success. Else, return all
    * errors, in the same order.
    *
    * The `version` field of the returned `Project` in case of success may not be equal to the
    * provided one, in case the latter is not a specific version (e.g. version interval). Which
    * version get chosen depends on the repository implementation.
    */
  def fetchOne[F[_]](
    repositories: Seq[Repository],
    module: Module,
    version: VersionConstraint0,
    fetch: Repository.Fetch[F],
    fetchs: Seq[Repository.Fetch[F]]
  )(implicit
    F: Gather[F]
  ): EitherT[F, Seq[String], (ArtifactSource, Project)] = {

    val f: Repository.Fetch[F] = { a =>
      fetchs.foldLeft(fetch(a))((acc, f) => acc.leftFlatMap(_ => f(a)))
    }

    def versionOrError0(
      results: Either[String, (Versions, String)],
      ver: Either[
        coursier.version.VersionInterval,
        (Latest, Option[coursier.version.VersionInterval])
      ]
    ): Either[String, coursier.version.Version] =
      results match {
        case Right((v, listingUrl)) =>
          val selectedOpt = ver match {
            case Left(itv) =>
              v.inInterval(itv)
            case Right((kind, _)) =>
              v.latest0(kind)
          }
          selectedOpt match {
            case Some(selectedVer) =>
              ver match {
                case Right((kind, Some(itv))) if !itv.contains(selectedVer) =>
                  Left(
                    v.latest0(kind) match {
                      case None =>
                        s"No latest ${kind.name} version found in $listingUrl"
                      case Some(v0) =>
                        if (v0 == selectedVer.repr)
                          s"Latest ${kind.name} $v0 from $listingUrl not in ${itv.repr}"
                        else
                          s"Latest ${kind.name} $v0 from $listingUrl not retained"
                    }
                  )
                case _ =>
                  Right(selectedVer)
              }
            case None =>
              Left {
                ver match {
                  case Left(itv) =>
                    s"No version found for ${itv.repr} in $listingUrl"
                  case Right((kind, _)) =>
                    s"No latest ${kind.name} version found in $listingUrl"
                }
              }
          }
        case Left(e) =>
          Left(e)
      }

    def versionOrError(
      results: Seq[Either[String, (Versions, String)]],
      ver: Either[
        coursier.version.VersionInterval,
        (Latest, Option[coursier.version.VersionInterval])
      ]
    ): Either[Seq[String], (coursier.version.Version, Repository)] = {
      // FIXME We're sometimes trapping errors here (left elements in results)
      val found = results.zip(repositories)
        .collect {
          case (Right((v, listingUrl)), repo) =>
            val selectedOpt = ver match {
              case Left(itv) =>
                v.inInterval(itv)
              case Right((kind, _)) =>
                v.latest0(kind)
            }
            (selectedOpt, repo, listingUrl)
        }
        .collect {
          case (Some(v), repo, listingUrl) =>
            (v, repo, listingUrl)
        }
      if (found.isEmpty)
        Left(
          results.map {
            case Left(e) => e
            case Right((_, listingUrl)) =>
              ver match {
                case Left(itv) =>
                  s"No version found for ${itv.repr} in $listingUrl"
                case Right((kind, _)) =>
                  s"No latest ${kind.name} version found in $listingUrl"
              }
          }
        )
      else {
        val (selectedVer, repo, _) = found.maxBy(_._1)
        ver match {
          case Right((kind, Some(itv))) if !itv.contains(selectedVer) =>
            Left(
              results.map {
                case Left(e) => e
                case Right((v, listingUrl)) =>
                  v.latest0(kind) match {
                    case None =>
                      s"No latest ${kind.name} version found in $listingUrl"
                    case Some(v0) =>
                      if (v0 == selectedVer.repr)
                        s"Latest ${kind.name} $v0 from $listingUrl not in ${itv.repr}"
                      else
                        s"Latest ${kind.name} $v0 from $listingUrl not retained"
                  }
              }
            )
          case _ =>
            Right((selectedVer, repo))
        }
      }
    }

    def get(
      fetch: Repository.Fetch[F],
      intervalOpt: Option[Either[
        coursier.version.VersionInterval,
        (Latest, Option[coursier.version.VersionInterval])
      ]] = None
    ) = {

      val lookups = repositories
        .map { repo =>
          intervalOpt match {
            case None =>
              repo -> repo.find0(module, version, fetch).run
            case Some(itv) =>
              repo -> repo.versions(module, fetch, versionsCheckHasModule = false).flatMap {
                case (v, s) =>
                  EitherT(F.point(versionOrError0(Right((v, s)), itv))).flatMap { retainedVer =>
                    repo.find0(module, VersionConstraint0.fromVersion(retainedVer), fetch)
                  }
              }.run
          }
        }

      val task0 =
        lookups.foldLeft[F[Either[Seq[String], (ArtifactSource, Project)]]](F.point(Left(Nil))) {
          case (acc, (_, eitherProjTask)) =>
            acc.flatMap {
              case Left(errors) =>
                eitherProjTask.map(_.left.map(error => error +: errors))
              case res @ Right(_) =>
                F.point(res)
            }
        }

      val task =
        task0.map(e => e.left.map(_.reverse): Either[Seq[String], (ArtifactSource, Project)])
      EitherT(task)
    }

    def getLatest0(ver: Either[
      coursier.version.VersionInterval,
      (Latest, Option[coursier.version.VersionInterval])
    ]) = {
      val shouldParallelize = ver.exists(_._1 == Latest.Integration)
      if (shouldParallelize)
        EitherT[F, Seq[String], (ArtifactSource, Project)] {

          val lookups = F.gather(repositories.map(_.versions(module, f).run))

          def getLatest(v: coursier.version.Version, repo: Repository, fetch: Repository.Fetch[F]) =
            repo
              .find0(module, VersionConstraint0.fromVersion(v), fetch)
              .leftMap(err => repositories.map(r => if (r == repo) err else "")) // kind of meh

          lookups.flatMap { results =>
            EitherT(F.point(versionOrError(results, ver))).flatMap {
              case (v, repo) =>
                fetchs.foldLeft(getLatest(v, repo, fetch))(_ orElse getLatest(v, repo, _))
            }.run
          }
        }
      else
        fetchs.foldLeft(get(fetch, intervalOpt = Some(ver)))((acc, f) =>
          acc.orElse(get(f, intervalOpt = Some(ver)))
        )
    }

    def default = fetchs.foldLeft(get(fetch))(_ orElse get(_))

    if (version.asString.contains("&")) {
      val versions = version.asString.split('&').toSeq.distinct
      assert(versions.length == 2)

      val parsed = versions.map(s => Latest(s).toRight(s))
      val latest = parsed.collect { case Right(l) => l }
      val other = parsed.collect {
        case Left(v) =>
          coursier.version.VersionParse.versionConstraint(v)
      }

      assert(latest.length <= 1)

      if (latest.isEmpty) default
      else {
        val latest0 = latest.head
        assert(other.head.preferred.isEmpty)
        val itv = other.head.interval

        getLatest0(Right((latest0, Some(itv))))
      }
    }
    else
      Latest(version.asString) match {
        case Some(kind) =>
          getLatest0(Right((kind, None)))
        case None =>
          if (version.interval == coursier.version.VersionInterval.zero)
            default
          else
            getLatest0(Left(version.interval))
      }
  }

  def fetchOne[F[_]](
    repositories: Seq[Repository],
    module: Module,
    version: String,
    fetch: Repository.Fetch[F],
    fetchs: Seq[Repository.Fetch[F]]
  )(implicit
    F: Gather[F]
  ): EitherT[F, Seq[String], (ArtifactSource, Project)] =
    fetchOne(
      repositories,
      module,
      VersionConstraint0(version),
      fetch,
      fetchs
    )(F)

  def fetch0[F[_]](
    repositories: Seq[Repository],
    fetch: Repository.Fetch[F],
    fetchs: Seq[Repository.Fetch[F]] = Nil
  )(implicit
    F: Gather[F]
  ): Fetch0[F] =
    modVers =>
      F.gather {
        modVers.map {
          case (module, version) =>
            fetchOne(repositories, module, version, fetch, fetchs).run.map(d =>
              (module, version) -> d
            )
        }
      }.map(_.toSeq)

  @deprecated("Use fetch0 instead", "2.1.25")
  def fetch[F[_]](
    repositories: Seq[Repository],
    fetch: Repository.Fetch[F],
    fetchs: Seq[Repository.Fetch[F]] = Nil
  )(implicit
    F: Gather[F]
  ): Fetch[F] = {
    val f = fetch0(repositories, fetch, fetchs)
    modVers =>
      val modVers0 = modVers.map {
        case (mod, ver) =>
          (mod, VersionConstraint0(ver))
      }
      F.map(f(modVers0)) { l =>
        l.map {
          case ((mod, ver), value) =>
            ((mod, ver.asString), value)
        }
      }
  }

  def defaultMaxIterations: Int = 100

  def apply(resolution: Resolution): ResolutionProcess = {
    val resolution0 = resolution.nextIfNoMissing

    if (resolution0.isDone)
      Done(resolution0)
    else
      Missing(resolution0.missingFromCache.toSeq, resolution0, apply)
  }

  private[coursier] def fetchAll[F[_]](
    modVers: Seq[(Module, VersionConstraint0)],
    fetch: ResolutionProcess.Fetch0[F]
  )(implicit
    F: Monad[F]
  ): F[Vector[((Module, VersionConstraint0), Either[Seq[String], (ArtifactSource, Project)])]] = {

    def uniqueModules(modVers: Seq[(Module, VersionConstraint0)])
      : LazyList[Seq[(Module, VersionConstraint0)]] = {

      val res = modVers.groupBy(_._1).toSeq.map(_._2).map {
        case Seq(v) => (v, Nil)
        case Seq()  => sys.error("Cannot happen")
        case v =>
          val res = v.maxBy(_._2)
          (res, v.filter(_ != res))
      }

      val other = res.flatMap(_._2)

      if (other.isEmpty)
        LazyList(modVers)
      else {
        val missing0 = res.map(_._1)
        missing0 #:: uniqueModules(other)
      }
    }

    uniqueModules(modVers)
      .toVector
      .foldLeft(F.point(
        Vector.empty[((Module, VersionConstraint0), Either[Seq[String], (ArtifactSource, Project)])]
      )) {
        (acc, l) =>
          for {
            v <- acc
            e <- fetch(l)
          } yield v ++ e
      }
  }

}
