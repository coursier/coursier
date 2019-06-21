package coursier.cli.publish

import java.io.{File, PrintStream}
import java.net.URI
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService}

import caseapp._
import com.lightbend.emoji.ShortCodes.Defaults.defaultImplicit.emoji
import coursier.cache.internal.ThreadUtil
import coursier.publish.checksum.{ChecksumType, Checksums}
import coursier.publish.fileset.{FileSet, Group}
import coursier.cli.publish.options.PublishOptions
import coursier.cli.publish.params.PublishParams
import coursier.publish.upload._
import coursier.cli.publish.util.{DeleteOnExit, Git}
import coursier.cli.util.Guard
import coursier.maven.MavenRepository
import coursier.publish.download.{Download, FileDownload, OkhttpDownload}
import coursier.util.{Sync, Task}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Publish extends CaseApp[PublishOptions] {

  val defaultChecksums = Seq(ChecksumType.MD5, ChecksumType.SHA1)

  private def repoParams(
    repo: MavenRepository,
    expect100Continue: Boolean = false,
    dummyUpload: Boolean = false,
    urlSuffix: String = ""
  ): (Upload, Download, MavenRepository, Boolean) = {

    val (upload, download, repo0, isLocal) =
      // TODO Accept .\ too on Windows?
      if (!repo.root.contains("://") && repo.root.contains(File.separatorChar)) {
        val p = Paths.get(repo.root).toAbsolutePath
        (FileUpload(p), FileDownload(p), repo.copy(root = "."), true)
      } else if (repo.root.startsWith("file:")) {
        val p = Paths.get(new URI(repo.root)).toAbsolutePath
        (FileUpload(p), FileDownload(p), repo.copy(root = "."), true)
      } else if (repo.root.startsWith("http://") || repo.root.startsWith("https://")) {
        val pool = Sync.fixedThreadPool(if (expect100Continue) 1 else 4) // sizing, shutdown, …
        val upload = OkhttpUpload.create(pool, expect100Continue, urlSuffix)
        (upload, OkhttpDownload.create(pool), repo, false)
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

  def publish(params: PublishParams, out: PrintStream, es: ScheduledExecutorService): Task[Unit] = {

    val deleteOnExit = new DeleteOnExit(params.verbosity)

    val now = Instant.now()

    params.maybeWarnSigner(out)

    val hooks = params.hooks(out, es)

    for {

      _ <- params.initSigner

      manualPackageFileSetOpt <- Input.manualPackageFileSetOpt(params, now)
      dirFileSet0 <- Input.dirFileSet(params, out)
      sbtFileSet0 <- Input.sbtFileSet(
        params,
        now,
        out,
        deleteOnExit,
        manualPackageFileSetOpt.isEmpty,
        es
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

      updateFileSet0 <- {
        val scmDomainPath = {
          val dir = params.directory.directories.headOption
            .orElse(params.directory.sbtDirectories.headOption)
            .map(_.toFile)
            .getOrElse(new File("."))
          if (params.metadata.git.getOrElse(params.repository.gitHub))
            Git(dir)
          else
            None
        }
        fileSet0.updateMetadata(
          params.metadata.organization,
          params.metadata.name,
          params.metadata.version,
          params.metadata.licenses,
          params.metadata.developersOpt,
          params.metadata.homePage,
          scmDomainPath,
          now
        )
      }

      isSnapshot0 <- isSnapshot(updateFileSet0)

      (_, readDownload, readRepo, _) =
        repoParams(params.repository.repository.readRepo(isSnapshot0))

      fileSet1 <- {
        if (params.metadata.mavenMetadata.getOrElse(!params.repository.gitHub))
          PublishTasks.updateMavenMetadata(
            updateFileSet0,
            now,
            readDownload,
            readRepo,
            params.downloadLogger(out),
            params.repository.snapshotVersioning
          )
        else
          Task.point {
            PublishTasks.clearMavenMetadata(updateFileSet0)
          }
      }

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
        val checksums = params.checksum.checksumsOpt.getOrElse {
          if (params.repository.gitHub || params.repository.bintray)
            Nil
          else
            defaultChecksums
        }
        if (checksums.isEmpty)
          Task.point(
            Checksums.clear(ChecksumType.all, withSignatures)
          )
        else
          Checksums(
            checksums,
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

      hooksData <- hooks.beforeUpload(sortedFinalFileSet, isSnapshot0)

      retainedRepo = hooks.repository(hooksData, params.repository.repository, isSnapshot0)
        .getOrElse(params.repository.repository.repo(isSnapshot0))

      parallel = params.parallel.getOrElse(params.repository.gitHub)
      urlSuffix = params.urlSuffixOpt.getOrElse(if (params.repository.bintray) ";publish=1" else "")

      (upload, _, repo, isLocal) = {
        repoParams(
          retainedRepo,
          dummyUpload = params.dummy,
          expect100Continue = parallel,
          urlSuffix = urlSuffix
        )
      }

      res <- upload.uploadFileSet(
        repo,
        sortedFinalFileSet,
        params.uploadLogger(out, isLocal),
        parallel = parallel
      )
      _ <- {
        if (res.isEmpty)
          Task.point(())
        else
          Task.fail(new PublishError.UploadingError(repo, res))
      }

      _ <- hooks.afterUpload(hooksData)
    } yield {
      if (params.verbosity >= 0) {
        val actualReadRepo = params.repository.repository.checkResultsRepo(isSnapshot0)
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

    val es = ThreadUtil.fixedScheduledThreadPool(params.cache.parallel)
    val ec = ExecutionContext.fromExecutorService(es)

    val task = publish(params, System.err, es)
    val f = task.attempt.future()(ec)
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
