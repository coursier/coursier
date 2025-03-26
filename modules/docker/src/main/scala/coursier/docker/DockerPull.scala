package coursier.docker

import coursier.cache.{Cache, CacheLogger, FileCache}
import coursier.cache.util.{Cpu, Os}
import coursier.core.Authentication
import coursier.util.{Artifact, Task}
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import dataclass.data

import java.io.File
import java.nio.file.Files

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object DockerPull {

  def defaultAuthRegistry: String = "https://auth.docker.io/token"

  def defaultOs: String = "linux"

  private def defaultArchAndVariant: (String, Option[String]) =
    Cpu.get() match {
      case Cpu.X86_64 => ("amd64", /* ??? */ None)
      case Cpu.Arm64  => ("arm64", Some("v8"))
    }

  def defaultArch: String =
    defaultArchAndVariant._1
  def defaultArchVariant: Option[String] =
    defaultArchAndVariant._2

  def pull(
    repoName: String,
    repoVersion: String,
    os: String = defaultOs,
    arch: String = defaultArch,
    archVariant: Option[String] = defaultArchVariant,
    authRegistry: String = defaultAuthRegistry,
    cache: Cache[Task] = FileCache()
  ): DockerPull.Result = {

    lazy val token = DockerUtil.token(authRegistry, repoName)

    val auth = Authentication.byNameBearerToken(token)

    // FIXME repo and version escaping!!!!
    val indexArtifact =
      Artifact(s"https://registry-1.docker.io/v2/$repoName/manifests/$repoVersion")
        .withChanging(true)
        .withAuthentication(Some(auth))

    val logger = cache.loggerOpt.getOrElse(CacheLogger.nop)

    val indexFile = Await.result(
      logger.using(cache.file(indexArtifact).run).future()(cache.ec),
      Duration.Inf
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(f)  => f
    }

    val index = {
      val content = Files.readAllBytes(indexFile.toPath)
      readFromArray(content)(DockerImageIndex.codec)
    }

    val manifestEntries = index.manifests.filter { entry =>
      entry.platform.get("architecture").forall(_ == arch) &&
      archVariant.forall(variant => entry.platform.get("variant").forall(_ == variant)) &&
      entry.platform.get("os").forall(_ == os)
    }

    val selectedManifestEntry = manifestEntries match {
      case Seq() =>
        System.err.println("No manifest found for the current architecture")
        sys.exit(1)
      case Seq(entry) => entry
      case other =>
        System.err.println("Found too many manifests for the current architecture:")
        for (m <- other)
          System.err.println(m.toString)
        sys.exit(1)
    }

    val manifestArtifact =
      Artifact(s"https://registry-1.docker.io/v2/$repoName/blobs/${selectedManifestEntry.digest}")
        .withAuthentication(Some(auth))
    val manifestFile = Await.result(
      logger.using(cache.file(manifestArtifact).run).future()(cache.ec),
      Duration.Inf
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(f)  => f
    }

    val manifest = {
      val content = Files.readAllBytes(manifestFile.toPath)
      readFromArray(content)(DockerImageManifest.codec)
    }

    val configArtifact =
      Artifact(s"https://registry-1.docker.io/v2/$repoName/blobs/${manifest.config.digest}")
        .withAuthentication(Some(auth))
    val configFile = Await.result(
      logger.using(cache.file(configArtifact).run).future()(cache.ec),
      Duration.Inf
    ) match {
      case Left(err) => throw new Exception(err)
      case Right(f)  => f
    }

    val config = {
      val content = Files.readAllBytes(configFile.toPath)
      readFromArray(content)(DockerImageConfig.codec)
    }

    val layerArtifacts = manifest.layers.map { layer =>
      Artifact(s"https://registry-1.docker.io/v2/$repoName/blobs/${layer.digest}")
        .withAuthentication(Some(auth))
    }
    val layerFilesOrErrors = {
      val task = logger.using(Task.gather.gather(layerArtifacts.map(cache.file(_).run)))
      Await.result(task.future()(cache.ec), Duration.Inf)
    }

    val layerFileErrors = layerFilesOrErrors.collect {
      case Left(err) => err
    }

    layerFileErrors match {
      case Seq() =>
      case Seq(first, others @ _*) =>
        val ex = new Exception(first)
        for (other <- others)
          ex.addSuppressed(other)
        throw ex
    }

    val layerFiles = layerFilesOrErrors.collect {
      case Right(f) => f
    }

    assert(layerArtifacts.length == layerFiles.length)

    DockerPull.Result(
      (indexArtifact, indexFile, index),
      selectedManifestEntry,
      (manifestArtifact, manifestFile, manifest),
      (configArtifact, configFile, config),
      layerArtifacts.zip(layerFiles)
    )
  }

  @data class Result(
    indexResult: (Artifact, File, DockerImageIndex),
    manifestEntry: DockerImageIndex.Entry,
    manifestResult: (Artifact, File, DockerImageManifest),
    configResult: (Artifact, File, DockerImageConfig),
    layerResults: Seq[(Artifact, File)]
  ) {
    def indexArtifact: Artifact =
      indexResult._1
    def indexFile: File =
      indexResult._2
    def index: DockerImageIndex =
      indexResult._3

    def manifestArtifact: Artifact =
      manifestResult._1
    def manifestFile: File =
      manifestResult._2
    def manifest: DockerImageManifest =
      manifestResult._3

    def configArtifact: Artifact =
      configResult._1
    def configFile: File =
      configResult._2
    def config: DockerImageConfig =
      configResult._3

    def layerArtifacts: Seq[Artifact] =
      layerResults.map(_._1)
    def layerFiles: Seq[File] =
      layerResults.map(_._2)

    def layerResults0: Seq[(Artifact, os.Path)] =
      layerResults.map {
        case (art, f) =>
          (art, os.Path(f))
      }
  }

}
