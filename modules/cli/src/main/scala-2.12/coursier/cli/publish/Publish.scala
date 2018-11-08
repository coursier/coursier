package coursier.cli.publish

import java.io.PrintStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.concurrent.TimeUnit

import caseapp._
import cats.data.Validated
import com.lightbend.emoji.ShortCodes.Defaults.defaultImplicit.emoji
import com.squareup.okhttp.OkHttpClient
import coursier.cli.Fetch
import coursier.cli.options.shared.RepositoryOptions
import coursier.cli.options.{CommonOptions, FetchOptions}
import coursier.cli.publish.checksum.{ChecksumType, Checksums, SimpleChecksumLogger}
import coursier.cli.publish.dir.{Dir, SimpleDirLogger}
import coursier.cli.publish.fileset.{FileSet, Group}
import coursier.cli.publish.options.PublishOptions
import coursier.cli.publish.params.PublishParams
import coursier.cli.publish.sbt.Sbt
import coursier.cli.publish.signing.{GpgSigner, Signer, SimpleSignerLogger}
import coursier.cli.publish.sonatype.{SimpleSonatypeLogger, SonatypeApi, SonatypeLogger}
import coursier.cli.publish.upload._
import coursier.cli.publish.util.DeleteOnExit
import coursier.maven.MavenRepository
import coursier.util.{Schedulable, Task}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Publish extends CaseApp[PublishOptions] {

  def updateMavenMetadata(
    fs: FileSet,
    now: Instant,
    upload: Upload,
    repository: MavenRepository,
    logger: Upload.Logger
  ): Task[FileSet] = {

    val groups = Group.split(fs)

    for {
      groups0 <- Group.addOrUpdateMavenMetadata(groups, now)
      fromRepo <- Group.downloadMavenMetadata(groups.collect { case m: Group.Module => (m.organization, m.name) }, upload, repository, logger)
      metadata <- Group.mergeMavenMetadata(fromRepo ++ groups0.collect { case m: Group.MavenMetadata => m }, now)
      groups1 = groups0.flatMap {
        case _: Group.MavenMetadata => Nil
        case m => Seq(m)
      } ++ metadata
      res <- Task.fromEither(Group.merge(groups1).left.map(msg => new Exception(msg)))
    } yield res
  }

  def sonatypeProfile(fs: FileSet, api: SonatypeApi, logger: SonatypeLogger): Task[SonatypeApi.Profile] = {

    val groups = Group.split(fs)
    val orgs = groups.map(_.organization).distinct

    api.listProfiles(logger).flatMap { profiles =>
      val m = orgs.map { org =>
        val validProfiles = profiles.filter(p => org.value == p.name || org.value.startsWith(p.name + "."))
        val profileOpt =
          if (validProfiles.isEmpty)
            None
          else
            Some(validProfiles.minBy(_.name.length))
        org -> profileOpt
      }

      val noProfiles = m.collect {
        case (org, None) => org
      }

      if (noProfiles.isEmpty) {
        val m0 = m.collect {
          case (org, Some(p)) => org -> p
        }

        val grouped = m0.groupBy(_._2)

        if (grouped.size > 1)
          Task.fail(new Exception(s"Cannot publish to several Sonatype profiles at once (${grouped.keys.toVector.map(_.name).sorted})"))
        else {
          assert(grouped.size == 1)
          Task.point(grouped.head._1)
        }
      } else
        Task.fail(new Exception(s"No Sonatype profile found to publish under organization(s) ${noProfiles.map(_.value).sorted.mkString(", ")}"))
    }

  }

  def repoParams(repo: MavenRepository) =
    // TODO Accept .\ too on Windows?
    if (repo.root.startsWith("/") || repo.root.startsWith("./"))
      (FileUpload(Paths.get(repo.root).toAbsolutePath), repo.copy(root = "."), true)
    else if (repo.root.startsWith("file:"))
      (FileUpload(Paths.get(new URI(repo.root)).toAbsolutePath), repo.copy(root = "."), true)
    else if (repo.root.startsWith("http://") || repo.root.startsWith("https://")) {
      val pool = Schedulable.fixedThreadPool(4) // sizing…
      (OkhttpUpload.create(pool), repo, false)
    } else
      throw new PublishError.UnrecognizedRepositoryFormat(repo.root)

  def publish(params: PublishParams, out: PrintStream): Task[Unit] = {

    val deleteOnExit = new DeleteOnExit(params.verbosity)

    val now = Instant.now()

    val snapshotRepoParams0 = repoParams(params.repository.repository.repo)
    val releaseRepoParams0 = repoParams(params.repository.repository.releaseRepo)
    val snapshotReadRepoParams0 = repoParams(params.repository.repository.readRepo)
    val releaseReadRepoParams0 = repoParams(params.repository.repository.readReleaseRepo)

    val manualPackageFileSetOpt =
      if (params.singlePackage.`package`)
        Manual.manualPackageFileSet(params.singlePackage, params.metadata, now) match {
          case Left(err) =>
            throw err
          case Right(fs) =>
            Some(fs)
        }
      else
        None

    lazy val sbtStructureJar = {
      val repositoryOptions = RepositoryOptions(
        repository = List("sbt-plugin:releases")
      )
      val commonOptions = CommonOptions(repositoryOptions = repositoryOptions)
      val fetchOptions = FetchOptions(common = commonOptions)
      val fetch = Fetch(fetchOptions, RemainingArgs(Seq("org.jetbrains:sbt-structure-extractor;scalaVersion=2.12;sbtVersion=1.0:2018.2.1"), Nil))
      fetch.files0 match {
        case Seq() => ???
        case Seq(jar) => jar
        case other => ???
      }
    }

    def dirName(dir: Path, short: Option[String] = None): String =
      if (params.verbosity >= 2)
        dir.normalize().toAbsolutePath.toString
      else
        short.getOrElse(dir.getFileName.toString)

    val dirFileSet = params
      .directory
      .directories
      .map { d =>
        Dir.readAndUpdate(
          params.metadata,
          now,
          params.verbosity,
          SimpleDirLogger.create(out, dirName(d), params.verbosity),
          d
        )
      }
      // the logger will have to be shared if this is to be parallelized
      .foldLeft(Task.point(FileSet.empty)) { (acc, t) =>
        for {
          a <- acc
          extra <- t
        } yield a ++ extra
      }

    val sbtFileSet = params
      .directory
      .sbtDirectories
      .map { sbtDir =>
        Task.delay {
          val sbt = new Sbt(sbtDir.toFile, sbtStructureJar, ExecutionContext.global, params.sbtOutputFrame, params.verbosity)
          val tmpDir = Files.createTempDirectory("coursier-publish-sbt-")
          deleteOnExit(tmpDir)
          val f = sbt.publishTo(tmpDir.toFile)
          // meh, blocking from a task…
          Await.result(f, Duration.Inf)
          Dir.readAndUpdate(
            params.metadata,
            now,
            params.verbosity,
            SimpleDirLogger.create(out, dirName(tmpDir, Some("temporary directory")), params.verbosity),
            tmpDir
          )
        }.flatMap(identity)
      }
      // DirLogger will have to be shared to parallelize this
      .foldLeft(Task.point(FileSet.empty)) { (acc, t) =>
        for {
          a <- acc
          extra <- t
        } yield a ++ extra
      }

    val signerOpt: Option[Signer] =
      params.signature.gpgKeyOpt match {
        case None =>
          if (params.repository.sonatypeOpt.nonEmpty)
            out.println("Warning: --sonatype passed, but no signing key specified, trying to proceed anyway")
          None
        case Some(key) =>
          Some(GpgSigner(key))
      }

    val initSigner = signerOpt.fold(Task.point(())) { signer =>
      // Signing dummy stuff to trigger any gpg dialog, before our signer logger is set up.
      // The gpg dialog and our logger seem to conflict else, leaving the terminal in a bad state.
      signer
        .sign(Content.InMemory(now, "hello".getBytes(StandardCharsets.UTF_8)))
        .flatMap {
          case Left(msg) => Task.fail(new Exception(
            s"Failed to sign: $msg"
          ))
          case Right(_) => Task.point(())
        }
    }


    val sonatypeApiOpt = params.repository.sonatypeOpt.map { sonatypeParams =>
      // this can't be shutdown anyway…
      val client = new OkHttpClient
      client.setReadTimeout(30L, TimeUnit.SECONDS)
      val authentication = params.repository.repository.repo.authentication
      if (authentication.isEmpty && params.verbosity >= 0)
        out.println("Warning: no Sonatype credentials passed, trying to proceed anyway")
      SonatypeApi(client, sonatypeParams.restBase, params.repository.repository.repo.authentication, params.verbosity)
    }

    val signerLogger = SimpleSignerLogger.create(out, params.verbosity)
    val checksumLogger = SimpleChecksumLogger.create(out, params.verbosity)
    val downloadLogger = new SimpleDownloadLogger(out, params.verbosity)
    val sonatypeLogger = SimpleSonatypeLogger.create(out, params.verbosity)

    for {
      _ <- initSigner
      dirFileSet0 <- dirFileSet
      sbtFileSet0 <- sbtFileSet
      fileSet0 = (manualPackageFileSetOpt.toSeq ++ Seq(dirFileSet0, sbtFileSet0)).foldLeft(FileSet.empty)(_ ++ _)
      _ <- {
        if (fileSet0.isEmpty)
          Task.fail(new PublishError.NoInput)
        else
          Task.point(())
      }
      sonatypeProfileOpt <- {
        sonatypeApiOpt match {
          case None =>
            Task.point(None)
          case Some(api) =>
            sonatypeProfile(fileSet0, api, sonatypeLogger)
              .map(Some(_))
        }
      }
      _ = {
        for (p <- sonatypeProfileOpt)
          if (params.verbosity >= 2)
            out.println(s"Selected Sonatype profile ${p.name} (id: ${p.id}, uri: ${p.uri})")
          else if (params.verbosity >= 1)
            out.println(s"Selected Sonatype profile ${p.name} (id: ${p.id})")
          else if (params.verbosity >= 0)
            out.println(s"Selected Sonatype profile ${p.name}")
      }
      isSnapshot <- {
          val versions = Group.split(fileSet0).collect {
            case m: Group.Module => m.version
          }
          val snapshotMap = versions.groupBy(_.endsWith("SNAPSHOT"))
          if (snapshotMap.size >= 2)
            Task.fail(new Exception("Cannot push both snapshot and non-snapshot artifacts"))
          else if (snapshotMap.get(false).nonEmpty)
            Task.point(false)
          else
            Task.point(true)
      }
      _ <- sonatypeProfileOpt match {
        case None => Task.point(())
        case Some(p) =>
          if (isSnapshot)
            Task.point(())
          else
            Task.fail(new Exception(s"Actual Sonatype REST actions not supported yet (would have pushed to profile ${p.name})"))
      }
      (upload, repo, isLocal) = {
        val (upload0, repo0, isLocal0) =
          if (isSnapshot)
            snapshotRepoParams0
          else
            releaseRepoParams0
        val actualUpload =
          if (params.dummy)
           DummyUpload(upload0)
          else
            upload0
        (actualUpload, repo0, isLocal0)
      }
      // readUpload…
      (readUpload, readRepo) = {
        val (upload0, repo0, _) =
          if (isSnapshot)
            snapshotReadRepoParams0
          else
            releaseReadRepoParams0
        val actualUpload =
          if (params.dummy)
            // just in case…
            DummyUpload(upload0)
          else
            upload0
        (actualUpload, repo0)
      }
      fileSet1 <- updateMavenMetadata(fileSet0, now, readUpload, readRepo, downloadLogger)
      _ <- initSigner // re-init signer (e.g. in case gpg-agent cleared its cache since the first init)
      withSignatures <- signerOpt.fold(Task.point(fileSet1)) { signer =>
        signer
          .signatures(
            fileSet1,
            now,
            ChecksumType.all.map(_.extension).toSet,
            Set("maven-metadata.xml"),
            signerLogger
          )
          .flatMap {
            case Left((path, _, msg)) => Task.fail(new Exception(
              s"Failed to sign $path: $msg"
            ))
            case Right(fs) => Task.point(fileSet1 ++ fs)
          }
      }
      finalFileSet <- {
        if (params.checksum.checksums.isEmpty)
          Task.point(withSignatures)
        else
          Checksums(
            params.checksum.checksums,
            withSignatures,
            now,
            checksumLogger
          ).map(withSignatures ++ _)
      }
      uploadLogger = SimpleUploadLogger.create(out, params.dummy, isLocal)
      res <- upload.uploadFileSet(repo, finalFileSet, uploadLogger)
      _ <- {
        if (res.isEmpty)
          Task.point(())
        else
          Task.fail(new PublishError.UploadingError(repo, res))
      }
    } yield {
      if (params.verbosity >= 0) {
        val modules = Group.split(finalFileSet).collect { case m: Group.Module => m }
        out.println(s"\n ${emoji("eyes").mkString} Check results at")
        for (m <- modules) {
          val base = readRepo.root.stripSuffix("/") + m.baseDir.map("/" + _).mkString
          out.println(s"  $base")
        }
      }
    }
  }

  def run(options: PublishOptions, args: RemainingArgs): Unit =
    PublishParams(options, args.all) match {

      case Validated.Invalid(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)

      case Validated.Valid(params) =>

        val task = publish(params, System.err)
        val f = task.attempt.future()(ExecutionContext.global)
        val res = Await.result(f, Duration.Inf)

        res match {
          case Left(err: PublishError) =>
            System.err.println(err.message)
            if (params.verbosity >= 2)
              err.printStackTrace(System.err)
            sys.exit(1)

          case Left(e) =>
            throw e

          case Right(()) =>
            // normal exit
        }
    }
}
