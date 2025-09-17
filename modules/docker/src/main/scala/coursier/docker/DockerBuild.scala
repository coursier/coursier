package coursier.docker

import coursier.cache.{
  ArchiveCache,
  DigestArtifact,
  DigestBasedArchiveCache,
  DigestBasedCache,
  FileCache
}
import coursier.docker.DockerFile.WithLines
import coursier.util.Task
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

import java.io.{BufferedInputStream, InputStream, OutputStream}
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPOutputStream

import scala.collection.immutable.SortedSet
import scala.util.Using
import scala.util.Properties
import coursier.docker.vm.Vm

object DockerBuild {

  def sha256(is: InputStream): String = {
    val md   = MessageDigest.getInstance("SHA-256")
    val buf  = Array.ofDim[Byte](16 * 1024)
    var read = 0
    while ({
      read = is.read(buf)
      read >= 0
    })
      md.update(buf, 0, read)
    val b   = md.digest()
    val num = new BigInteger(1, b)
    String.format("%064x", num)
  }

  def sha256(path: os.Path): String =
    Using.resource(os.read.inputStream(path)) { is =>
      sha256(new BufferedInputStream(is))
    }

  def layerArchive(
    dir: os.Path,
    archivePath: os.Path,
    maybeSudo: DockerRun.MaybeSudo
  ): Option[(os.Path, String)] = {
    val entries = maybeSudo.list(dir).map(_.relativeTo(dir).asSubPath.toString)
    if (entries.isEmpty)
      None
    else {
      maybeSudo.sudoWith(
        "tar",
        "--sort=name",
        "--mtime=1970-01-01 00:00:00",
        "-zcf",
        archivePath.toString,
        entries
      )(
        cwd = dir
      )
      maybeSudo.perms.set(archivePath, "rw-r--r--")

      val sum = sha256(archivePath)

      Some((archivePath, sum))
    }
  }

  def build(
    contextDir: os.Path,
    dockerFile: Option[os.Path],
    vmOpt: Option[Vm],
    authRegistry: String = DockerPull.defaultAuthRegistry,
    cache: FileCache[Task] = FileCache(),
    os0: String = DockerPull.defaultOs,
    arch: String = DockerPull.defaultArch,
    archVariant: Option[String] = DockerPull.defaultArchVariant
  ): (DockerImageConfig.Config, List[(DockerImageLayer, os.Path)]) = {

    val dockerFile0     = dockerFile.getOrElse(contextDir / "Dockerfile")
    val dockerFileLines = os.read.lines(dockerFile0)

    val res = DockerFile.parse(
      dockerFileLines.iterator.zipWithIndex.map {
        case (line, idx) =>
          WithLines(line, SortedSet(idx))
      }
    ).toTry.get

    val (from, others) = DockerFile.validateShape(res).toTry.get

    val (repoName, repoVersion) = DockerUtil.repoNameVersion(from.value.imageName)

    build(
      repoName,
      repoVersion,
      contextDir,
      authRegistry,
      cache,
      others,
      dockerFile0.toString,
      os0,
      arch,
      archVariant,
      vmOpt
    )
  }

  def build(
    fromRepoName: String,
    fromRepoVersion: String,
    contextDir: os.Path,
    authRegistry: String,
    cache: FileCache[Task],
    instructions: Seq[WithLines[DockerInstruction.NonHead]],
    dockerFile: String, // for error messages
    os0: String,
    arch: String,
    archVariant: Option[String],
    vmOpt: Option[Vm]
  ): (DockerImageConfig.Config, List[(DockerImageLayer, os.Path)]) = {

    val dockerPullResults = DockerPull.pull(
      fromRepoName,
      fromRepoVersion,
      authRegistry = authRegistry,
      cache = cache,
      os = os0,
      arch = arch,
      archVariant = archVariant
    )

    var config = DockerImageConfig.Config(
      Seq(
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=xterm"
      ),
      Nil,
      None
    )
    var layers = List.empty[(DockerImageLayer, os.Path)]
    var shell  = Seq("/bin/sh", "-c")

    val uuid          = UUID.randomUUID()
    val containerName = s"${DockerRun.defaultContainerName}-build-$uuid"

    val priviledgedArchiveCache       = ArchiveCache.priviledged()
    val priviledgedDigestArchiveCache = DigestBasedArchiveCache(priviledgedArchiveCache)
    val digestCache                   = DigestBasedCache()

    val maybeSudo = new DockerRun.MaybeSudo(true)

    for (inst <- instructions)
      inst.value match {
        case cmd: DockerInstruction.Cmd =>
          config = config.copy(
            Cmd = cmd.command
          )
        case workDir: DockerInstruction.WorkDir =>
          config = config.copy(
            WorkingDir = Some(workDir.path)
          )
        case expose: DockerInstruction.Expose =>
          config = config.copy(
            // WorkingDir = Some(workDir.path)
          )
          System.err.println(s"Ignore exposed instruction $expose")
        case copy: DockerInstruction.Copy =>
          val from = os.FilePath(copy.from) match {
            case p: os.Path    => p
            case s: os.SubPath => contextDir / s
            case r: os.RelPath =>
              // just discard the superfluous '..' elements
              // (Docker is supposed to do the same per https://docs.docker.com/reference/dockerfile/#copy)
              contextDir / os.SubPath(r.segments)
          }
          if (!copy.to.startsWith("/"))
            sys.error(
              s"$dockerFile:${inst.lines.head}: Expected COPY destination to start with '/'"
            )
          val to = os.SubPath(copy.to.stripPrefix("/"))

          val tmpDir = os.temp.dir(prefix = "cs-docker-build", perms = "rwxr-xr-x")
          try {
            val dest = tmpDir / "dest"
            maybeSudo.makeDir.all(dest)
            maybeSudo.copy(from, dest / to, copyAttributes = true)
            maybeSudo.owner.set(dest / to, "root", "root")

            val archivePath = tmpDir / "archive.tar"

            val layerOpt = DockerBuild.layerArchive(
              dest,
              archivePath,
              maybeSudo
            )
            for ((path, digest) <- layerOpt) {
              val path0 =
                os.Path(digestCache.`import`(DigestArtifact(digest, path.toNIO)))
              layers =
                (
                  DockerImageLayer(os.size(path0), digest, DockerImageLayer.mediaType),
                  path0
                ) :: layers
            }
          }
          finally
            maybeSudo.remove.all(tmpDir)
        case run: DockerInstruction.Run =>
          val res = DockerRun.run(
            cache = cache,
            digestCache = digestCache,
            config = config.copy(
              Cmd = shell :+ run.command.mkString(" ")
            ),
            layerFiles =
              () => dockerPullResults.layerFiles.map(os.Path(_)) ++ layers.reverse.map(_._2),
            layerDirs = () => {
              val fromBase =
                DockerUnpack.unpack(priviledgedArchiveCache, dockerPullResults.layerArtifacts)
                  .map(os.Path(_))
              val fromBuiltLayersTasks = layers.reverse.map {
                case (info, path) =>
                  val art = DigestArtifact(info.digest, path.toNIO)
                  priviledgedDigestArchiveCache.get(art)
              }
              val fromBuiltLayersOrErrors =
                Task.gather.gather(fromBuiltLayersTasks).unsafeRun(wrapExceptions = true)(cache.ec)
              val fromBuiltErrors = fromBuiltLayersOrErrors.collect {
                case Left(err) => err
              }
              fromBuiltErrors match {
                case Seq()                   =>
                case Seq(first, others @ _*) =>
                  for (other <- others)
                    first.addSuppressed(other)
                  throw new Exception(first)
              }
              val fromBuiltLayers = fromBuiltLayersOrErrors.collect {
                case Right(f) => os.Path(f)
              }
              fromBase ++ fromBuiltLayers
            },
            containerName = containerName,
            vmOpt = vmOpt,
            withUpperDir = Some { upperDir =>

              val tmpDir = os.temp.dir(prefix = "cs-docker-build", perms = "rwxr-xr-x")
              try {
                val archivePath = tmpDir / "archive.tar.gz"

                val layerOpt = DockerBuild.layerArchive(
                  upperDir,
                  archivePath,
                  maybeSudo
                )
                for ((path, digest) <- layerOpt) {
                  val path0 =
                    os.Path(digestCache.`import`(DigestArtifact(digest, path.toNIO)))
                  layers =
                    (
                      DockerImageLayer(os.size(path0), digest, DockerImageLayer.mediaType),
                      path0
                    ) :: layers
                }
              }
              finally
                maybeSudo.remove.all(tmpDir)
            },
            withUpperDirArchive = Some { upperDirArchive =>

              val archiveIsEmpty =
                Using.resource(os.read.inputStream(upperDirArchive)) { is =>
                  new TarArchiveInputStream(is).getNextEntry() == null
                }
              val layerOpt =
                if (archiveIsEmpty) None
                else {
                  val dest = upperDirArchive / os.up / s"${upperDirArchive.last}.gz"
                  final class PatchOutputStream(underlying: OutputStream) extends OutputStream {
                    var count                  = 0
                    val patchByteAtIndex       = 9
                    val forcedPatchedByteValue = 3
                    def write(byte: Int): Unit = {
                      underlying.write(
                        if (count == patchByteAtIndex) forcedPatchedByteValue
                        else byte
                      )
                      if (count <= patchByteAtIndex)
                        count += 1
                    }
                    override def write(
                      b: Array[Byte],
                      off: Int,
                      len: Int
                    ): Unit = {
                      if (count <= patchByteAtIndex && count + len > patchByteAtIndex) {
                        underlying.write(b, off, patchByteAtIndex - count)
                        underlying.write(forcedPatchedByteValue)
                        underlying.write(
                          b,
                          off + patchByteAtIndex - count + 1,
                          len - (patchByteAtIndex - count + 1)
                        )
                      }
                      else
                        underlying.write(b, off, len)
                      if (count <= patchByteAtIndex)
                        count += len
                    }
                  }
                  Using.resources(
                    os.read.inputStream(upperDirArchive),
                    os.write.outputStream(dest)
                  ) {
                    (is, os) =>
                      val gzos = new GZIPOutputStream(new PatchOutputStream(os))
                      val buf  = Array.ofDim[Byte](16 * 1024)
                      var read = 0
                      while ({
                        read = is.read(buf)
                        read >= 0
                      })
                        gzos.write(buf, 0, read)
                      gzos.finish()
                      gzos.flush()
                  }
                  // FIXME Ideally, we could compute the checksum on-the-fly above
                  Some((dest, sha256(dest)))
                }

              for ((path, digest) <- layerOpt) {
                val path0 =
                  os.Path(digestCache.`import`(DigestArtifact(digest, path.toNIO)))
                layers =
                  (
                    DockerImageLayer(os.size(path0), digest, DockerImageLayer.mediaType),
                    path0
                  ) :: layers
              }
            }
          )
          if (res.exitCode != 0) {
            System.err.println(
              s"$dockerFile:${inst.lines.head}: command failed with exit code ${res.exitCode}"
            )
            sys.exit(res.exitCode)
          }
      }

    (config, layers.reverse)
  }

}
