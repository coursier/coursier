package coursier.cache

import coursier.cache.TestUtil._
import coursier.paths.CachePath
import coursier.util.{Artifact, Task}
import org.apache.commons.compress.archivers.tar.{
  TarArchiveEntry,
  TarArchiveOutputStream,
  TarConstants
}
import org.codehaus.plexus.archiver.ArchiverException
import utest._

import java.nio.charset.StandardCharsets

import scala.util.{Failure, Properties, Try, Using}

object ArchiveCacheSymbolicLinkTests extends TestSuite {

  private def writeTar(dest: os.Path, entries: Seq[(String, Either[String, String])]): Unit =
    Using.resource(new TarArchiveOutputStream(os.write.outputStream(dest, createFolders = true))) {
      tos =>
        for ((name, content) <- entries)
          content match {
            case Left(text) =>
              val bytes = text.getBytes(StandardCharsets.UTF_8)
              val entry = new TarArchiveEntry(name)
              entry.setSize(bytes.length)
              tos.putArchiveEntry(entry)
              tos.write(bytes)
              tos.closeArchiveEntry()
            case Right(target) =>
              val entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK)
              entry.setLinkName(target)
              tos.putArchiveEntry(entry)
              tos.closeArchiveEntry()
          }
    }

  private final case class Setup(
    archiveCache: ArchiveCache[Task],
    archive: os.Path,
    victim: os.Path
  ) {
    def artifact: Artifact                    = Artifact(archive.toNIO.toUri.toASCIIString)
    def get(): Either[ArtifactError, os.Path] =
      archiveCache.get(artifact).unsafeRun()(archiveCache.cache.ec).map(os.Path(_))
    // where the archive cache extracts the archive
    def extractionDir: os.Path =
      os.Path(CachePath.localFile(artifact.url, archiveCache.location, null, true))
    def checkVictim(): Unit = {
      assert(os.isDir(victim))
      assert(os.read(victim / "precious.txt") == "precious")
    }
  }

  private def withSetup[T](f: Setup => T): T =
    withTmpDir { dir =>
      val victim = dir / "victim"
      os.write(victim / "precious.txt", "precious", createFolders = true)
      val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
        .copy(cache = FileCache[Task]((dir / "cache").toIO))
      f(Setup(archiveCache, dir / "archives" / "archive.tar", victim))
    }

  private def checkRejected(entries: Seq[(String, Either[String, String])]): Unit =
    withSetup { setup =>
      writeTar(setup.archive, entries)
      // twice, so that the second run cleans up what the first one left behind
      for (_ <- 1 to 2) {
        Try(setup.get()) match {
          case Failure(ex: ArchiverException) =>
            assert(ex.getMessage.contains("Symbolic link"))
          case other =>
            throw new Exception(s"Expected extraction to fail, got $other")
        }
        setup.checkVictim()
      }
      assert(!os.exists(setup.extractionDir))
    }

  val tests =
    if (Properties.isWin) Tests {} // creating symbolic links requires specific rights there
    else
      Tests {

        test("links inside archive accepted") {
          withSetup { setup =>
            writeTar(
              setup.archive,
              Seq(
                "sub/file"     -> Left("content"),
                "a"            -> Right("sub/file"),
                "self"         -> Right("."),
                "sub/up"       -> Right("../a"),
                "sub/parent"   -> Right(".."),
                "sub/through"  -> Right("parent/sub/file"),
                "sub/dangling" -> Right("../nope/nope")
              )
            )
            val dir = setup.get().fold(e => throw new Exception(e), identity)
            assert(os.read(dir / "a") == "content")
            assert(os.read(dir / "sub" / "up") == "content")
            assert(os.read(dir / "sub" / "through") == "content")
            assert(os.isLink(dir / "sub" / "dangling"))
          }
        }

        test("absolute link rejected") {
          withSetup { setup =>
            writeTar(setup.archive, Seq("s" -> Right(setup.victim.toString)))
            Try(setup.get()) match {
              case Failure(ex: ArchiverException) =>
                assert(ex.getMessage.contains("absolute target"))
              case other =>
                throw new Exception(s"Expected extraction to fail, got $other")
            }
            setup.checkVictim()
          }
        }

        test("relative link outside archive rejected") {
          checkRejected(Seq("s" -> Right("../../../victim")))
          checkRejected(Seq("sub/s" -> Right("../..")))
          checkRejected(Seq("sub/s" -> Right("./../x/../../y")))
        }

        test("link outside archive rejected before being extracted") {
          withSetup { setup =>
            writeTar(setup.archive, Seq("s" -> Right("../victim"), "file" -> Left("content")))
            Try(setup.get()) match {
              case Failure(_: ArchiverException) =>
              case other                         =>
                throw new Exception(s"Expected extraction to fail, got $other")
            }
            val tmp = os.Path(CachePath.temporaryFile(setup.extractionDir.toIO))
            // the partial extraction is removed on failure
            assert(!os.exists(tmp))
            setup.checkVictim()
          }
        }

        test("relative link outside archive through other link rejected") {
          // sub/c resolves to the extraction directory, sub/b to its parent
          checkRejected(
            Seq(
              "sub/file" -> Left("content"),
              "sub/c"    -> Right(".."),
              "sub/b"    -> Right("c/../victim")
            )
          )
        }

        test("symbolic link loop rejected") {
          checkRejected(Seq("a" -> Right("b"), "b" -> Right("a")))
        }

        test("leftover temporary directory not followed") {
          withSetup { setup =>
            writeTar(setup.archive, Seq("file" -> Left("content")))
            // A temporary directory left by a previous failed extraction, with an escaping link
            val tmp = os.Path(CachePath.temporaryFile(setup.extractionDir.toIO))
            os.makeDir.all(tmp)
            os.symlink(tmp / "s", setup.victim)
            val dir = setup.get().fold(e => throw new Exception(e), identity)
            assert(os.read(dir / "file") == "content")
            assert(!os.exists(tmp, followLinks = false))
            setup.checkVictim()
          }
        }

        test("former extraction directory not followed") {
          withSetup { setup =>
            writeTar(setup.archive, Seq("file" -> Left("content")))
            val dir = setup.get().fold(e => throw new Exception(e), identity)
            // Escaping link left by an extraction made by an earlier coursier version
            os.symlink(dir / "s", setup.victim)
            // Archive changed, so that it gets extracted again
            writeTar(setup.archive, Seq("file" -> Left("new content")))
            os.mtime.set(setup.archive, os.mtime(dir) + 10000L)
            val dir0 = setup.get().fold(e => throw new Exception(e), identity)
            assert(dir0 == dir)
            assert(os.read(dir / "file") == "new content")
            assert(!os.exists(dir / "s", followLinks = false))
            setup.checkVictim()
          }
        }
      }
}
