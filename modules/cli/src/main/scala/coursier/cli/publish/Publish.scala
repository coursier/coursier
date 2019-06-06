package coursier.cli.publish

import java.io.{File, PrintStream}
import java.net.URI
import java.nio.file.{Path, Paths}
import java.time.Instant

import caseapp._
import com.lightbend.emoji.ShortCodes.Defaults.defaultImplicit.emoji
import coursier.publish.checksum.{ChecksumType, Checksums}
import coursier.publish.dir.Dir
import coursier.publish.dir.logger.DirLogger
import coursier.publish.fileset.{FileSet, Group}
import coursier.cli.publish.options.PublishOptions
import coursier.cli.publish.params.{MetadataParams, PublishParams}
import coursier.publish.upload._
import coursier.cli.publish.util.DeleteOnExit
import coursier.cli.util.Guard
import coursier.maven.MavenRepository
import coursier.publish.download.{Download, FileDownload, OkhttpDownload}
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Publish extends CaseApp[PublishOptions] {

  private def repoParams(repo: MavenRepository, dummyUpload: Boolean = false): (Upload, Download, MavenRepository, Boolean) = {

    val (upload, download, repo0, isLocal) =
      // TODO Accept .\ too on Windows?
      if (!repo.root.contains("://") && repo.root.contains(File.separatorChar)) {
        val p = Paths.get(repo.root).toAbsolutePath
        (FileUpload(p), FileDownload(p), repo.copy(root = "."), true)
      } else if (repo.root.startsWith("file:")) {
        val p = Paths.get(new URI(repo.root)).toAbsolutePath
        (FileUpload(p), FileDownload(p), repo.copy(root = "."), true)
      } else if (repo.root.startsWith("http://") || repo.root.startsWith("https://")) {
        val pool = Sync.fixedThreadPool(4) // sizing, shutdown, …
        (OkhttpUpload.create(pool), OkhttpDownload.create(pool), repo, false)
      } else
        throw new PublishError.UnrecognizedRepositoryFormat(repo.root)

    val actualUpload =
      if (dummyUpload) DummyUpload(upload)
      else upload

    (actualUpload, download, repo0, isLocal)
  }

  private def isSnapshot(fs: FileSet): Task[Boolean] = {
    val versions = Group.split(fs).collect {
      case m: Group.Module => m.version
    }
    val snapshotMap = versions.groupBy(_.endsWith("SNAPSHOT"))
    if (snapshotMap.size >= 2)
      Task.fail(new Exception("Cannot push both snapshot and non-snapshot artifacts"))
    else
      Task.point(!snapshotMap.contains(false))
  }

  // Move to Input?
  def readAndUpdateDir(
    metadata: MetadataParams,
    now: Instant,
    verbosity: Int,
    logger: => DirLogger,
    dir: Path
  ): Task[FileSet] =
    Dir.read(dir, logger).flatMap { fs =>
      fs.updateMetadata(
        metadata.organization,
        metadata.name,
        metadata.version,
        metadata.licenses,
        metadata.developersOpt,
        metadata.homePage,
        now
      )
    }

  def publish(params: PublishParams, out: PrintStream): Task[Unit] = {

    val deleteOnExit = new DeleteOnExit(params.verbosity)

    val now = Instant.now()

    params.maybeWarnSigner(out)

    val hooks = Hooks.sonatype(params.sonatypeApiOpt(out), out, params.verbosity, params.batch)

    for {

      _ <- params.initSigner

      manualPackageFileSetOpt <- Input.manualPackageFileSetOpt(params, now)
      dirFileSet0 <- Input.dirFileSet(params, now, out)
      sbtFileSet0 <- Input.sbtFileSet(
        params,
        now,
        out,
        deleteOnExit,
        manualPackageFileSetOpt.isEmpty
      )
      fileSet0 = (manualPackageFileSetOpt.toSeq ++ Seq(dirFileSet0, sbtFileSet0)).foldLeft(FileSet.empty)(_ ++ _)
      _ = {
        if (params.verbosity >= 2) {
          System.err.println(s"Initial file set (${fileSet0.elements.length} elements)")
          for ((p, _) <- fileSet0.elements)
            System.err.println("  " + p.repr)
          System.err.println()
        }
      }
      _ <- {
        if (fileSet0.isEmpty)
          Task.fail(new PublishError.NoInput)
        else
          Task.point(())
      }

      isSnapshot0 <- isSnapshot(fileSet0)

      (_, readDownload, readRepo, _) =
        repoParams(params.repository.repository.readRepo(isSnapshot0))

      fileSet1 <- PublishTasks.updateMavenMetadata(
        fileSet0,
        now,
        readDownload,
        readRepo,
        params.downloadLogger(out),
        params.repository.snapshotVersioning
      )

      _ <- params.initSigner // re-init signer (e.g. in case gpg-agent cleared its cache since the first init)
      withSignatures <- {
        params
          .signer
          .signatures(
            fileSet1,
            now,
            ChecksumType.all.map(_.extension).toSet,
            Set("maven-metadata.xml"),
            params.signerLogger(out)
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
            params.checksumLogger(out)
          ).map(withSignatures ++ _)
      }

      sortedFinalFileSet <- finalFileSet.order

      _ = {
        if (params.verbosity >= 2) {
          System.err.println(s"Writing / pushing ${sortedFinalFileSet.elements.length} elements:")
          for ((f, _) <- sortedFinalFileSet.elements)
            System.err.println(s"  ${f.repr}")
        }
      }

      hooksData <- hooks.beforeUpload(fileSet0, isSnapshot0)

      retainedRepo = hooks.repository(hooksData, params.repository.repository, isSnapshot0)
        .getOrElse(params.repository.repository.repo(isSnapshot0))

      (upload, _, repo, isLocal) = {
        repoParams(
          retainedRepo,
          dummyUpload = params.dummy
        )
      }

      res <- upload.uploadFileSet(repo, sortedFinalFileSet, params.uploadLogger(out, isLocal))
      _ <- {
        if (res.isEmpty)
          Task.point(())
        else
          Task.fail(new PublishError.UploadingError(repo, res))
      }

      _ <- hooks.afterUpload(hooksData)
    } yield {
      if (params.verbosity >= 0) {
        val actualReadRepo = params.repository.repository.readRepo(isSnapshot0)
        val modules = Group.split(sortedFinalFileSet)
          .collect { case m: Group.Module => m }
          .sortBy(m => (m.organization.value, m.name.value, m.version))
        out.println(s"\n ${emoji("eyes").mkString} Check results at")
        for (m <- modules) {
          val base = actualReadRepo.root.stripSuffix("/") + m.baseDir.map("/" + _).mkString
          out.println(s"  $base")
        }
        // TODO If publishing releases to Sonatype and not promoting, print message about how to promote things.
        // TODO If publishing releases to Sonatype, print message about Maven Central sync.
      }
    }
  }

  def run(options: PublishOptions, args: RemainingArgs): Unit = {

    Guard()

    val params = PublishParams(options, args.all).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(p) => p
    }

    val task = publish(params, System.err)
    val f = task.attempt.future()(ExecutionContext.global)
    val res = Await.result(f, Duration.Inf)

    res match {
      case Left(err: PublishError) if params.verbosity <= 1 =>
        System.err.println(err.message)
        sys.exit(1)

      // Kind of meh to catch those here.
      // These errors should be returned via Either-s or other and handled explicitly.
      case Left(err: Upload.Error) if params.verbosity <= 1 =>
        System.err.println(err.getMessage)
        sys.exit(1)

      case Left(e) =>
        throw e

      case Right(()) =>
        // normal exit
    }
  }
}
