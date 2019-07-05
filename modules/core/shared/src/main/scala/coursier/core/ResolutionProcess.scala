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
        case done: Done =>
          F.point(done.resolution)
        case missing0: Missing =>
          F.bind(ResolutionProcess.fetchAll[F](missing0.missing, fetch))(result =>
            missing0.next0(result).run[F](fetch, maxIterations0)
          )
        case cont: Continue =>
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
      case _: Done =>
        F.point(this)
      case missing0: Missing =>
        F.map(ResolutionProcess.fetchAll(missing0.missing, fetch))(result => missing0.next0(result))
      case cont: Continue =>
        if (fastForward)
          cont.nextNoCont.next(fetch, fastForward = fastForward)
        else
          F.point(cont.next)
    }

  def current: Resolution
}

final class Missing private (
  val missing: Seq[(Module, String)],
  val current: Resolution,
  val cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Missing =>
        missing == other.missing &&
          current == other.current &&
          cont == other.cont
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.core.Missing".##
    code = 37 * code + missing.##
    code = 37 * code + current.##
    code = 37 * code + cont.##
    37 * code
  }

  override def toString: String =
    s"Missing($missing, $current, $cont)"


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

      val depMgmtMissing = depMgmtMissing0.map(_._2).fold(Set.empty)(_ ++ _)

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

object Missing {
  def apply(
    missing: Seq[(Module, String)],
    current: Resolution,
    cont: Resolution => ResolutionProcess
  ): Missing =
    new Missing(missing, current, cont)
}

final class Continue private (
  val current: Resolution,
  val cont: Resolution => ResolutionProcess
) extends ResolutionProcess {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Continue =>
        current == other.current &&
          cont == other.cont
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.core.Continue".##
    code = 37 * code + current.##
    code = 37 * code + cont.##
    37 * code
  }

  override def toString: String =
    s"Continue($current, $cont)"


  def next: ResolutionProcess = cont(current)

  @tailrec def nextNoCont: ResolutionProcess =
    next match {
      case nextCont: Continue => nextCont.nextNoCont
      case other => other
    }

}

object Continue {
  def apply(
    current: Resolution,
    cont: Resolution => ResolutionProcess
  ): Continue =
    new Continue(current, cont)
}

final class Done private (val resolution: Resolution) extends ResolutionProcess {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Done =>
        resolution == other.resolution
      case _ => false
    }

  override def hashCode(): Int = {
    var code = 17 + "coursier.core.Done".##
    code = 37 * code + resolution.##
    37 * code
  }

  override def toString: String =
    s"Done($resolution)"


  def current: Resolution = resolution
}

object Done {
  def apply(resolution: Resolution): Done =
    new Done(resolution)
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
    fetchs: Seq[Repository.Fetch[F]],
    listVersionFetch: Repository.Fetch[F] = null,
    listVersionFetchs: Seq[Repository.Fetch[F]] = null
  )(implicit
    F: Gather[F]
  ): EitherT[F, Seq[String], (Artifact.Source, Project)] = {

    def getLatest(ver: Either[VersionInterval, (Latest, Option[VersionInterval])], fetch: Repository.Fetch[F]) = {

      val lookups = repositories.map(_.versions(module, fetch).run)

      val versionOrError: F[Either[Seq[String], (Version, Repository)]] =
        F.map(F.gather(lookups)) { results =>
          // FIXME We're sometimes trapping errors here (left elements in results)
          val found = results.zip(repositories)
            .collect {
              case (Right((v, listingUrl)), repo) =>
                val selectedOpt = ver match {
                  case Left(itv) =>
                    v.inInterval(itv)
                  case Right((kind, _)) =>
                    v.latest(kind)
                }
                (selectedOpt, repo, listingUrl)
            }
            .collect {
              case (Some(v), repo, listingUrl) =>
                (Version(v), repo, listingUrl)
            }
          if (found.isEmpty)
            Left(
              results.map {
                case Left(e) => e
                case Right((_, listingUrl)) =>
                  ver match {
                    case Left(itv) =>
                      s"No version found for $itv in $listingUrl"
                    case Right((kind, _)) =>
                      s"No latest ${kind.name} version found in $listingUrl"
                  }
              }
            )
          else {
            val (selectedVer, repo, _) = found.maxBy(_._1)
            ver match {
              case Left(_) =>
                Right((selectedVer, repo))
              case Right((kind, intervalOpt)) =>
                intervalOpt match {
                  case None =>
                    Right((selectedVer, repo))
                  case Some(itv) =>
                    if (itv.contains(selectedVer))
                      Right((selectedVer, repo))
                    else
                      Left(
                        results.map {
                          case Left(e) => e
                          case Right((v, listingUrl)) =>
                            v.latest(kind) match {
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
                }
            }
          }
        }

      EitherT(versionOrError).flatMap {
        case (v, repo) =>
          repo
            .find(module, v.repr, fetch)
            .leftMap(err => repositories.map(r => if (r == repo) err else "")) // kind of meh
      }
    }

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

    def getLatest0(ver: Either[VersionInterval, (Latest, Option[VersionInterval])]) = {
      val listVersionFetch0 = Option(listVersionFetch).getOrElse(fetch)
      val listVersionFetchs0 = Option(listVersionFetchs).getOrElse {
        if (listVersionFetch == null) fetchs
        else Nil
      }
      (getLatest(ver, listVersionFetch0) /: listVersionFetchs0) (_ orElse getLatest(ver, _))
    }

    if (version.contains("&")) {
      val versions = version.split('&').toSeq.distinct
      assert(versions.length == 2)

      val parsed = versions.map(s => Latest(s).toRight(s))
      val latest = parsed.collect { case Right(l) => l }
      val other = parsed.collect { case Left(v) => Parse.versionConstraint(v) }
      assert(latest.length == 1)
      assert(other.length == 1)

      val latest0 = latest.head
      assert(other.head.preferred.isEmpty)
      val itv = other.head.interval

      getLatest0(Right((latest0, Some(itv))))
    } else
      Latest(version) match {
        case Some(kind) =>
          getLatest0(Right((kind, None)))
        case None =>
          val c = Parse.versionConstraint(version)
          if (c.interval == VersionInterval.zero)
            (get(fetch) /: fetchs)(_ orElse get(_))
          else
            getLatest0(Left(c.interval))
      }
  }

  def fetch[F[_]](
    repositories: Seq[core.Repository],
    fetch: Repository.Fetch[F],
    fetchs: Seq[Repository.Fetch[F]] = Nil,
    listVersionFetch: Repository.Fetch[F] = null,
    listVersionFetchs: Seq[Repository.Fetch[F]] = null
  )(implicit
    F: Gather[F]
  ): Fetch[F] =
    modVers =>
      F.map(
        F.gather {
          modVers.map {
            case (module, version) =>
              F.map(fetchOne(repositories, module, version, fetch, fetchs, listVersionFetch, listVersionFetchs).run)(d => (module, version) -> d)
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

