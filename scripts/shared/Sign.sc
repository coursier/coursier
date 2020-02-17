
import $file.Util

import java.nio.file.{Files, Path, Paths}
import java.util.Base64

private def defaultPgpPassphrase: String =
  sys.env.getOrElse(
    "PGP_PASSPHRASE",
    sys.error("PGP_PASSPHRASE not set")
  )

private lazy val gpgOptions: Seq[String] = {
  var opts = Seq("--batch=true", "--yes")
  Util.os match {
    case "mac" | "win" =>
      opts = opts ++ Seq("--pinentry-mode", "loopback")
    case _ =>
  }
  opts
}

private def defaultPgpSecret: String =
  sys.env.getOrElse(
    "PGP_SECRET",
    sys.error("PGP_SECRET not set")
  )

private def importGpgKey(pgpSecret: String): Unit = {
  val key = Base64.getDecoder.decode(pgpSecret)
  val cmd = Seq("gpg") ++ gpgOptions ++ Seq("--import")
  val b = new ProcessBuilder(cmd: _*)
    .redirectInput(ProcessBuilder.Redirect.PIPE)
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
  val p = b.start()
  val os = p.getOutputStream
  os.write(key)
  os.flush()
  os.close()
  val retCode = p.waitFor()
  if (retCode == 2)
    System.err.println("gpg import exited with return code 2, assuming key was already imported")
  else if (retCode != 0)
    sys.error(s"Command ${cmd.mkString(" ")} exited with return code $retCode")
}

private def sign(pgpPassphrase: String, path: String, sigPathOpt: Option[String] = None): String = {
  val sigPath = sigPathOpt.getOrElse(path + ".asc")
  val cmd = Seq("gpg") ++ gpgOptions ++ Seq("--passphrase", pgpPassphrase, "--detach-sign", "--armor", "--use-agent", "--output", sigPath, path)
  Util.run(cmd)
  sigPath
}

/**
 * Signs files with gpg.
 *
 * @param generatedFiles List of files to sign (first element is a local file path, second element is a virtual path for the file)
 * @param pgpPassphrase
 * @param pgpSecret
 *
 * @return `generatedFiles` augmented with the signature files
 */
def apply(
  generatedFiles: Seq[(String, String)],
  pgpPassphrase: Option[String] = Some(defaultPgpPassphrase),
  pgpSecret: Option[String] = Some(defaultPgpSecret)
): Seq[(Path, String)] = {

  for (s <- pgpSecret)
    importGpgKey(s)
  for (p <- pgpPassphrase) {
    val empty = Files.createTempFile("test-signing", "")
    sign(p, empty.toString) // test run
    Files.deleteIfExists(empty)
  }

  for ((path, _) <- generatedFiles) {
    val f = Paths.get(path)
    if (!Files.exists(f))
      sys.error(s"$path not found")
    if (!Files.isRegularFile(f))
      sys.error(s"$path is not a file")
  }

  generatedFiles.flatMap {
    case (path, assetName) =>
      val sigOpt = pgpPassphrase.map { p =>
        sign(p, path)
      }

      Seq(Paths.get(path) -> assetName) ++
        sigOpt.toSeq.map(p => Paths.get(p) -> s"$assetName.asc")
  }
}
