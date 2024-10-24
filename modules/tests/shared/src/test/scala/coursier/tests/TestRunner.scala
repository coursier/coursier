package coursier.tests

import utest._

import scala.async.Async.{async, await}
import coursier.core.{
  Attributes,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Module,
  Reconciliation,
  Repository,
  Resolution,
  ResolutionProcess
}
import coursier.maven.MavenRepository
import coursier.tests.compatibility.{textResource, tryCreate, updateSnapshots}
import coursier.tests.util.ToFuture
import coursier.util.{Artifact, Gather}

import scala.concurrent.{ExecutionContext, Future}

class TestRunner[F[_]: Gather: ToFuture](
  artifact: Repository.Fetch[F] = compatibility.taskArtifact,
  repositories: Seq[Repository] = Seq(MavenRepository("https://repo1.maven.org/maven2"))
)(implicit ec: ExecutionContext) {

  private def fetch(repositories: Seq[Repository]): ResolutionProcess.Fetch[F] =
    ResolutionProcess.fetch(repositories, artifact)

  def resolve(
    deps: Seq[Dependency],
    filter: Option[Dependency => Boolean] = None,
    extraRepos: Seq[Repository] = Nil,
    profiles: Option[Set[String]] = None,
    mapDependencies: Option[Dependency => Dependency] = None,
    forceVersions: Map[Module, String] = Map.empty,
    defaultConfiguration: Configuration = Configuration.defaultCompile,
    reconciliation: Option[Module => Reconciliation] = None,
    forceDepMgmtVersions: Option[Boolean] = None
  ): Future[Resolution] = {

    val repositories0 = extraRepos ++ repositories

    val fetch0 = fetch(repositories0)

    val res = Resolution()
      .withRootDependencies(deps)
      .withFilter(filter)
      .withUserActivations {
        profiles.map { profiles0 =>
          profiles0
            .iterator
            .map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true)
            .toMap
        }
      }
      .withMapDependencies(mapDependencies)
      .withForceVersions(forceVersions)
      .withDefaultConfiguration(defaultConfiguration)
      .withReconciliation(reconciliation)
      .withForceDepMgmtVersions(forceDepMgmtVersions.getOrElse(false))
    val r = ResolutionProcess(res).run(fetch0)

    val t = Gather[F].map(r) { res =>

      val metadataErrors = res.errors
      val conflicts      = res.conflicts
      val isDone         = res.isDone
      assert(metadataErrors.isEmpty)
      assert(conflicts.isEmpty)
      assert(isDone)

      res
    }

    ToFuture[F].toFuture(ec, t)
  }

  def resolution(
    module: Module,
    version: String,
    extraRepos: Seq[Repository] = Nil,
    configuration: Configuration = Configuration.empty,
    profiles: Option[Set[String]] = None,
    forceVersions: Map[Module, String] = Map.empty,
    defaultConfiguration: Configuration = Configuration.defaultCompile,
    forceDepMgmtVersions: Option[Boolean] = None
  ): Future[Resolution] =
    async {
      val attrPathPart =
        if (module.attributes.isEmpty)
          ""
        else
          "/" + module.attributes.toVector.sorted.map {
            case (k, v) => k + "_" + v
          }.mkString("_")

      val path = Seq(
        "resolutions",
        module.organization.value,
        module.name.value,
        attrPathPart,
        version + (
          if (configuration.isEmpty)
            ""
          else
            "_" + configuration.value.replace('(', '_').replace(')', '_')
        )
        // FIXME Take forceVersions, forceDepMgmtVersions into account too
      ).filter(_.nonEmpty).mkString("/")

      def tryRead = textResource(path)

      val dep = Dependency(module, version).withConfiguration(configuration)
      val res = await {
        resolve(
          Seq(dep),
          extraRepos = extraRepos,
          profiles = profiles,
          forceVersions = forceVersions,
          defaultConfiguration = defaultConfiguration,
          forceDepMgmtVersions = forceDepMgmtVersions
        )
      }

      val result = res
        .orderedDependencies
        .map { dep =>
          val projOpt = res.projectCache
            .get(dep.moduleVersion)
            .map { case (_, proj) => proj }
          val dep0 = dep.withVersion(projOpt.fold(dep.version)(_.actualVersion))
          (
            dep0.module.organization.value,
            dep0.module.nameWithAttributes,
            dep0.version,
            dep0.configuration.value
          )
        }
        .distinct
        .map {
          case (org, name, ver, cfg) =>
            Seq(org, name, ver, cfg).mkString(":")
        }

      val expected =
        await(
          tryRead.recoverWith {
            case _: Exception =>
              tryCreate(path, result.mkString("\n"))
              tryRead
          }
        ).split('\n').toSeq

      if (updateSnapshots) {
        if (result != expected)
          tryCreate(path, result.mkString("\n"))
      }
      else {
        if (result != expected)
          for (((e, r), idx) <- expected.zip(result).zipWithIndex if e != r)
            println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")

        assert(result == expected)
      }

      res
    }

  def resolutionCheck(
    module: Module,
    version: String,
    extraRepos: Seq[Repository] = Nil,
    configuration: Configuration = Configuration.empty,
    profiles: Option[Set[String]] = None,
    forceVersions: Map[Module, String] = Map.empty,
    forceDepMgmtVersions: Option[Boolean] = None
  ): Future[Unit] =
    resolution(
      module,
      version,
      extraRepos,
      configuration,
      profiles,
      forceVersions,
      forceDepMgmtVersions = forceDepMgmtVersions
    ).map(_ => ())

  def withArtifacts[T](
    module: Module,
    version: String,
    attributes: Attributes = Attributes.empty,
    extraRepos: Seq[Repository] = Nil,
    classifierOpt: Option[Classifier] = None,
    transitive: Boolean = false
  )(
    f: Seq[Artifact] => T
  ): Future[T] = {
    val dep = Dependency(module, version).withTransitive(transitive).withAttributes(attributes)
    withArtifacts(dep, extraRepos, classifierOpt)(f)
  }

  def withArtifacts[T](
    dep: Dependency,
    extraRepos: Seq[Repository],
    classifierOpt: Option[Classifier]
  )(
    f: Seq[Artifact] => T
  ): Future[T] =
    withArtifacts(Seq(dep), extraRepos, classifierOpt)(f)

  def withArtifacts[T](
    deps: Seq[Dependency],
    extraRepos: Seq[Repository],
    classifierOpt: Option[Classifier]
  )(
    f: Seq[Artifact] => T
  ): Future[T] =
    withDetailedArtifacts(deps, extraRepos, classifierOpt)(l => f(l.map(_._2)))

  def withDetailedArtifacts[T](
    deps: Seq[Dependency],
    extraRepos: Seq[Repository],
    classifierOpt: Option[Classifier]
  )(
    f: Seq[(Attributes, Artifact)] => T
  ): Future[T] =
    async {
      val res = await(resolve(deps, extraRepos = extraRepos))

      val metadataErrors = res.errors
      val conflicts      = res.conflicts
      val isDone         = res.isDone
      assert(metadataErrors.isEmpty)
      assert(conflicts.isEmpty)
      assert(isDone)

      val artifacts = res.dependencyArtifacts(classifiers = classifierOpt.map(Seq(_)))
        .map(t => (t._2.attributes, t._3))
        .distinct

      f(artifacts)
    }

  def ensureHasArtifactWithExtension(
    module: Module,
    version: String,
    extension: Extension,
    attributes: Attributes = Attributes.empty,
    extraRepos: Seq[Repository] = Nil
  ): Future[Unit] =
    withArtifacts(module, version, attributes = attributes, extraRepos = extraRepos) { artifacts =>
      assert(artifacts.exists(_.url.endsWith("." + extension.value)))
    }

}
