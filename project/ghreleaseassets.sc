// from https://github.com/coursier/coursier/blob/382250d4f26b4728400a0546088e27ca0f129e8b/scripts/shared/UploadGhRelease.sc

import $ivy.`com.softwaremill.sttp.client::core:2.0.0-RC6`
import $ivy.`com.lihaoyi::ujson:0.9.5`

import $file.docs, docs.gitRepoHasChanges
import $file.launchers, launchers.{platformExtension, platformSuffix}
import $file.publishing, publishing.{ghOrg, ghName}

import java.io._
import java.nio.ByteBuffer
import java.nio.charset.{MalformedInputException, StandardCharsets}
import java.util.zip.{ZipException, ZipFile}

import sttp.client.quick._

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.Properties

private def contentType(path: os.Path): String = {

  val isZipFile = {
    var zf: ZipFile = null
    try { zf = new ZipFile(path.toIO); true }
    catch { case _: ZipException => false }
    finally if (zf != null) zf.close()
  }

  lazy val isTextFile =
    try {
      StandardCharsets.UTF_8
        .newDecoder()
        .decode(ByteBuffer.wrap(os.read.bytes(path)))
      true
    }
    catch { case e: MalformedInputException => false }

  if (isZipFile) "application/zip"
  else if (isTextFile) "text/plain"
  else "application/octet-stream"
}

private def releaseId(
  ghOrg: String,
  ghProj: String,
  ghToken: String,
  tag: String
): Long = {
  val url = uri"https://api.github.com/repos/$ghOrg/$ghProj/releases"
  val resp = quickRequest
    .header("Accept", "application/vnd.github.v3+json")
    .header("Authorization", s"token $ghToken")
    .get(url)
    .send()

  val json = ujson.read(resp.body)
  val releaseId =
    try json
      .arr
      .find(_("tag_name").str == tag)
      .map(_("id").num.toLong)
      .getOrElse {
        val tags = json.arr.map(_("tag_name").str).toVector
        sys.error(s"Tag $tag not found (found tags: ${tags.mkString(", ")}")
      }
    catch {
      case NonFatal(e) =>
        System.err.println(resp.body)
        throw e
    }

  System.err.println(s"Release id is $releaseId")

  releaseId
}

def currentAssets(
  releaseId: Long,
  ghOrg: String,
  ghProj: String,
  ghToken: String
): Map[String, Long] = {

  val resp = quickRequest
    .header("Accept", "application/vnd.github.v3+json")
    .header("Authorization", s"token $ghToken")
    .get(uri"https://api.github.com/repos/$ghOrg/$ghProj/releases/$releaseId/assets")
    .send()
  val json = ujson.read(resp.body)
  json
    .arr
    .iterator
    .map { obj =>
      obj("name").str -> obj("id").num.toLong
    }
    .toMap
}

/** Uploads files as GitHub release assets.
  *
  * @param uploads
  *   List of local files / asset name to be uploaded
  * @param ghOrg
  *   GitHub organization of the release
  * @param ghProj
  *   GitHub project name of the release
  * @param ghToken
  *   GitHub token
  * @param tag
  *   Tag to upload assets to
  * @param dryRun
  *   Whether to run a dry run (printing the actions that would have been done, but not uploading
  *   anything)
  */
def upload(
  ghOrg: String,
  ghProj: String,
  ghToken: String,
  tag: String,
  dryRun: Boolean,
  overwrite: Boolean
)(
  uploads: (os.Path, String)*
): Unit = {

  val releaseId0 = releaseId(ghOrg, ghProj, ghToken, tag)

  val currentAssets0 =
    if (overwrite) currentAssets(releaseId0, ghOrg, ghProj, ghToken) else Map.empty[String, Long]

  for ((f0, name) <- uploads) {

    currentAssets0
      .get(name)
      .filter(_ => overwrite)
      .foreach { assetId =>
        val resp = quickRequest
          .header("Accept", "application/vnd.github.v3+json")
          .header("Authorization", s"token $ghToken")
          .delete(uri"https://api.github.com/repos/$ghOrg/$ghProj/releases/assets/$assetId")
          .send()
      }

    val uri =
      uri"https://uploads.github.com/repos/$ghOrg/$ghProj/releases/$releaseId0/assets?name=$name"
    val contentType0 = contentType(f0)
    System.err.println(s"Detected content type of $f0: $contentType0")
    if (dryRun)
      System.err.println(s"Would have uploaded $f0 as $name")
    else {
      System.err.println(s"Uploading $f0 as $name")
      quickRequest
        .header("Accept", "application/vnd.github.v3+json")
        .header("Authorization", s"token $ghToken")
        .body(f0.toNIO)
        .header("Content-Type", contentType0)
        .post(uri)
        .send()
    }
  }
}

def readInto(is: InputStream, os: OutputStream): Unit = {
  val buf  = Array.ofDim[Byte](1024 * 1024)
  var read = -1
  while ({
    read = is.read(buf)
    read >= 0
  }) os.write(buf, 0, read)
}

def writeInZip(name: String, file: os.Path, zip: os.Path): Unit = {
  import java.nio.file.attribute.FileTime
  import java.util.zip._

  os.makeDir.all(zip / os.up)

  var fis: InputStream      = null
  var fos: FileOutputStream = null
  var zos: ZipOutputStream  = null

  try {
    fis = os.read.inputStream(file)
    fos = new FileOutputStream(zip.toIO)
    zos = new ZipOutputStream(new BufferedOutputStream(fos))

    val ent = new ZipEntry(name)
    ent.setLastModifiedTime(FileTime.fromMillis(os.mtime(file)))
    ent.setSize(os.size(file))
    zos.putNextEntry(ent)
    readInto(fis, zos)
    zos.closeEntry()

    zos.finish()
  }
  finally {
    if (zos != null) zos.close()
    if (fos != null) fos.close()
    if (fis != null) fis.close()
  }
}

def copyLauncher(
  nativeLauncher: os.Path,
  directory: os.Path
): Unit = {
  val name = s"cs-$platformSuffix$platformExtension"
  if (Properties.isWin)
    writeInZip(name, nativeLauncher, directory / s"cs-$platformSuffix.zip")
  else {
    val dest = directory / name
    os.copy(nativeLauncher, dest, createFolders = true, replaceExisting = true)
    os.proc("gzip", "-v", dest.toString).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
  }
}

def uploadLaunchers(
  version: String,
  directory: os.Path
): Unit = {
  val launchers = os.list(directory).filter(os.isFile(_)).map { path =>
    path -> path.last
  }
  val ghToken = Option(System.getenv("UPLOAD_GH_TOKEN")).getOrElse {
    sys.error("UPLOAD_GH_TOKEN not set")
  }
  val (tag, overwriteAssets) =
    if (version.endsWith("-SNAPSHOT")) ("latest", true)
    else ("v" + version, false)
  upload(ghOrg, ghName, ghToken, tag, dryRun = false, overwrite = overwriteAssets)(launchers: _*)

  upload0(
    launchers,
    ghOrg,
    "launchers",
    "master",
    ghToken,
    s"Add $version launchers",
    dryRun = false
  )
}

private def upload0(
  generatedFiles: Seq[(os.Path, String)],
  ghOrg: String,
  ghProj: String,
  branch: String,
  ghToken: String,
  message: String,
  dryRun: Boolean
): Unit = {
  def proceed(): Unit =
    doUpload(
      generatedFiles,
      ghOrg,
      ghProj,
      branch,
      ghToken,
      message,
      dryRun
    )

  if (dryRun)
    proceed()
  else {
    @tailrec
    def loop(n: Int): Unit =
      if (n <= 1) proceed()
      else {
        val succeeded =
          try {
            proceed()
            true
          }
          catch {
            case NonFatal(e) =>
              System.err.println(s"Caught $e, trying again")
              false
          }

        if (!succeeded)
          loop(n - 1)
      }

    // concurrent attempts to push things to coursier/launchers might collide
    loop(3)
  }
}

private def doUpload(
  generatedFiles: Seq[(os.Path, String)],
  ghOrg: String,
  ghProj: String,
  branch: String,
  ghToken: String,
  message: String,
  dryRun: Boolean
): Unit =
  withTmpDir(s"$ghOrg-$ghProj-$branch") { tmpDir =>

    val repo = s"https://$ghToken@github.com/$ghOrg/$ghProj.git"
    System.err.println(s"Cloning ${repo.replace(ghToken, "****")} in $tmpDir")
    os.proc("git", "clone", repo, "-q", "-b", branch, tmpDir.toString).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    os.proc("git", "config", "user.name", "Github Actions").call(
      cwd = tmpDir,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    os.proc("git", "config", "user.email", "actions@github.com").call(
      cwd = tmpDir,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    for ((path, name) <- generatedFiles) {
      val dest = tmpDir / name.split("/").toSeq
      System.err.println(s"Copying $path to $dest")
      os.copy(path, dest, replaceExisting = true)
      os.proc("git", "add", "--", name).call(
        cwd = tmpDir,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )
    }

    val hasChanges = gitRepoHasChanges(tmpDir)
    if (hasChanges) {
      os.proc("git", "commit", "-m", message).call(
        cwd = tmpDir,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

      if (dryRun)
        System.err.println("Dummy mode, not pushing changes")
      else {
        // rebase on latest changes just in case
        System.err.println(s"Fetch latest changes for $branch")
        os.proc("git", "fetch", "origin").call(
          cwd = tmpDir,
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )
        System.err.println(s"Rebasing on latest changes for $branch if needed")
        os.proc("git", "rebase", s"origin/$branch").call(
          cwd = tmpDir,
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )

        System.err.println("Pushing changes")
        os.proc("git", "push", "origin", branch).call(
          cwd = tmpDir,
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )
      }
    }
    else
      System.err.println("Nothing changed")
  }

private def withTmpDir[T](prefix: String)(f: os.Path => T): T = {
  var tmpDir: os.Path = null
  try {
    tmpDir = os.temp.dir(prefix = prefix)
    f(tmpDir)
  }
  finally if (tmpDir != null) {
    System.err.println(s"Deleting $tmpDir")
    try os.remove.all(tmpDir)
    catch {
      case NonFatal(e) =>
        System.err.println(s"Warning: caught $e while deleting $tmpDir, ignoring it...")
        e.printStackTrace()
    }
  }
}
