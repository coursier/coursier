
import java.nio.ByteBuffer
import java.nio.charset.{MalformedInputException, StandardCharsets}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipException, ZipFile}

import sttp.client.quick._

import scala.util.control.NonFatal

private def contentType(path: Path): String = {

  val isZipFile =
    try {
      new ZipFile(path.toFile)
      true
    } catch {
      case _: ZipException =>
        false
    }

  lazy val isTextFile =
    try {
      StandardCharsets.UTF_8
        .newDecoder()
        .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
      true
    } catch {
      case e: MalformedInputException =>
        false
    }

  if (isZipFile)
    "application/zip"
  else if (isTextFile)
    "text/plain"
  else
    "application/octet-stream"
}

private def releaseId(
  ghOrg: String,
  ghProj: String,
  ghToken: String,
  version: String
): Long = {
  val url = uri"https://api.github.com/repos/$ghOrg/$ghProj/releases?access_token=$ghToken"
  val resp = quickRequest.get(url).send()

  val json = ujson.read(resp.body)
  val releaseId = 
    try {
      val tags = json.arr.map(_("tag_name").str).toVector
      json
        .arr
        .find(_("tag_name").str == s"v$version")
        .map(_("id").num.toLong)
        .getOrElse {
          sys.error(s"Tag v$version not found (found tags: ${tags.mkString(", ")}")
        }
    } catch {
      case NonFatal(e) =>
        System.err.println(url)
        System.err.println(resp.body)
        throw e
    }

  System.err.println(s"Release id is $releaseId")

  releaseId
}

/**
 * Uploads files as GitHub release assets.
 *
 * @param uploads List of local files / asset name to be uploaded
 * @param ghOrg GitHub organization of the release
 * @param ghProj GitHub project name of the release
 * @param ghToken GitHub token
 * @param version Version to upload assets to (assets are uploaded to tag `"v" + version`)
 * @param dryRun Whether to run a dry run (printing the actions that would have been done, but not uploading anything)
 */
def apply(
  uploads: Seq[(Path, String)],
  ghOrg: String,
  ghProj: String,
  ghToken: String,
  version: String,
  dryRun: Boolean
): Unit = {

  val releaseId0 = releaseId(ghOrg, ghProj, ghToken, version)

  for ((f0, name) <- uploads) {
    val uri = uri"https://uploads.github.com/repos/$ghOrg/$ghProj/releases/$releaseId0/assets?name=$name&access_token=$ghToken"
    val contentType0 = contentType(f0)
    System.err.println(s"Detected content type of $f0: $contentType0")
    if (dryRun)
      System.err.println(s"Would have uploaded $f0 as $name")
    else {
      System.err.println(s"Uploading $f0 as $name")
      quickRequest
        .body(f0)
        .header("Content-Type", contentType0)
        .post(uri)
        .send()
    }
  }
}
