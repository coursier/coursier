package mill.javalib

import mill.api.{BuildCtx, Logger}
import mill.javalib.PublishModule.PublishData
import mill.javalib.api.PgpWorkerApi
import mill.javalib.internal.PublishModule.GpgArgs
import mill.javalib.publish.SonatypeHelpers.{PASSWORD_ENV_VARIABLE_NAME, USERNAME_ENV_VARIABLE_NAME}
import mill.javalib.publish.{Artifact, PublishingType, SonatypeCredentials}
import mill.util.Tasks
import mill.{Task, given}

/** [[SonatypeCentralPublisher2]], minus the checksums of the PGP signatures.
  *
  * Maven Central wants `.md5` / `.sha1` files for the published artifacts, not for their `.asc`
  * signatures: Sonatype's own documented bundle layout has none of the latter
  * ([[https://central.sonatype.org/publish/publish-portal-upload/]]), and neither do artifacts
  * published with Maven or Gradle. Mill checksums the signatures too - it maps over
  * `fileMapping ++ signedArtifacts` in `SonatypeHelpers.buildArtifactMappings`
  * ([[https://github.com/com-lihaoyi/mill/blob/16168fbf7e20bc03aae85411d2122782eb98cf15/libs/javalib/src/mill/javalib/publish/SonatypeHelpers.scala#L211-L241]]) -
  * which uploads 8 files per artifact that nothing ever reads.
  *
  * Rather than re-implement the mapping, this drops those entries from it afterwards. The checksums
  * it throws away cost a few hashes of a few hundred bytes each.
  */
private class NoSignatureChecksumPublisher(
  credentials: SonatypeCredentials,
  gpgArgs: GpgArgs,
  pgpWorker: PgpWorkerApi,
  readTimeout: Int,
  connectTimeout: Int,
  log: Logger,
  env: Map[String, String],
  awaitTimeout: Int
) extends SonatypeCentralPublisher2(
      credentials = credentials,
      gpgArgs = gpgArgs,
      pgpWorker = pgpWorker,
      readTimeout = readTimeout,
      connectTimeout = connectTimeout,
      log = log,
      env = env,
      awaitTimeout = awaitTimeout
    ) {
  override protected def mapArtifacts(
    artifacts: Seq[(Map[os.SubPath, os.Path], Artifact)]
  ): Seq[(artifact: Artifact, contents: Map[os.SubPath, Array[Byte]])] =
    super.mapArtifacts(artifacts).map { entry =>
      entry.artifact -> entry.contents.filterNot { (path, _) =>
        path.last.endsWith(".asc.md5") || path.last.endsWith(".asc.sha1")
      }
    }
}

/** Publishes to Sonatype Central the way `mill.javalib.SonatypeCentralPublishModule` does, but
  * without the checksums of the PGP signatures (see [[NoSignatureChecksumPublisher]]).
  *
  * A trimmed down copy of Mill's own logic - no `gpg` CLI support - living in the `mill.javalib`
  * package so that it can reach the `private[mill]` publishing API it builds upon. Adapted from
  * `SonatypeCentralPublishModule.publishAll`, as of Mill 1.2.0-RC1-46-16168f:
  *   - [[https://github.com/com-lihaoyi/mill/blob/16168fbf7e20bc03aae85411d2122782eb98cf15/libs/javalib/src/mill/javalib/SonatypeCentralPublishModule.scala#L108-L150 the command]]
  *   - [[https://github.com/com-lihaoyi/mill/blob/16168fbf7e20bc03aae85411d2122782eb98cf15/libs/javalib/src/mill/javalib/SonatypeCentralPublishModule.scala#L152-L244 the logic it delegates to]]
  *
  * THIS FILE IS TEMPORARY. The signature checksums are a Mill bug rather than something coursier
  * wants to configure, so the fix belongs upstream, in `SonatypeHelpers.buildArtifactMappings`.
  * Once a Mill release carries it, delete this file, drop the `sonatype-central-client-requests`
  * dependency from `mill-build/build.mill`, take `CoursierSonatypeCentralPublish` back off
  * `object ci`, and point `.github/workflows/publish.yml` at
  * `mill.scalalib.SonatypeCentralPublishModule/` again.
  */
trait CoursierSonatypeCentralPublish extends MavenWorkerSupport, PgpWorkerSupport, MavenPublish {

  /** @param publishArtifacts
    *   the artifacts to publish
    * @param shouldRelease
    *   whether to release the uploaded bundle right away, rather than leave it to be published from
    *   https://central.sonatype.com/publishing/deployments
    * @param bundleName
    *   upload everything as a single bundle under that name, rather than one bundle per artifact
    * @param localRepo
    *   write the bundles to that directory instead of uploading them (to check what a release would
    *   look like)
    */
  def publishSonatypeCentral(
    // LocalOnlyPublishModule modules are never published to remote repositories
    publishArtifacts: Tasks[PublishData] =
      Tasks.resolveMainDefault("__:PublishModule:^LocalOnlyPublishModule.publishArtifacts"),
    shouldRelease: Boolean = true,
    bundleName: String = "",
    localRepo: String = "",
    username: String = "",
    password: String = "",
    readTimeout: Int = 60000,
    connectTimeout: Int = 5000,
    awaitTimeout: Int = 120 * 1000,
    snapshotUri: String = PublishModule.sonatypeCentralSnapshotUri
  ): Task.Command[Unit] = Task.Command {
    val all                   = Task.sequence(publishArtifacts.value)()
    val (snapshots, releases) = all.partition(_.meta.isSnapshot)
    val bundleName0           = Some(bundleName).filter(_.nonEmpty)
    val localRepo0 = Some(localRepo).filter(_.nonEmpty).map(os.Path(_, BuildCtx.workspaceRoot))

    if (bundleName0.nonEmpty && snapshots.nonEmpty)
      Task.fail(
        s"Cannot publish snapshot versions in a bundle ($bundleName), got " +
          s"${snapshots.length} snapshot and ${releases.length} release artifacts"
      )

    // not via PublishCredentialsModule: applying its task would pull it in the task graph, and
    // make even a --localRepo run fail when no credentials are around
    def credential(name: String, envVar: String, value: String): String =
      if (value.nonEmpty) value
      else
        Task.env.getOrElse(
          envVar,
          Task.fail(s"No Sonatype $name set, pass --$name or set $envVar")
        )
    lazy val credentials = (
      username = credential("username", USERNAME_ENV_VARIABLE_NAME, username),
      password = credential("password", PASSWORD_ENV_VARIABLE_NAME, password)
    )

    if (releases.nonEmpty) {
      // if this fails, publish nothing
      val gpgArgs = internal.PublishModule.pgpImportSecretIfProvidedAndMakeGpgArgs(
        Task.env,
        GpgArgs.fromUserProvided(""),
        pgpWorker()
      )
      // takes the credentials as an argument, so that a --localRepo run needs none
      def publisher(creds: SonatypeCredentials) = new NoSignatureChecksumPublisher(
        credentials = creds,
        gpgArgs = gpgArgs,
        pgpWorker = pgpWorker(),
        readTimeout = readTimeout,
        connectTimeout = connectTimeout,
        log = Task.log,
        env = Task.env,
        awaitTimeout = awaitTimeout
      )
      val artifacts = releases.map(_.withConcretePath)
      localRepo0 match {
        case Some(dir) =>
          Task.log.info(s"Writing ${releases.length} release artifacts to $dir")
          publisher(SonatypeCredentials("", ""))
            .publishAllToLocal(dir, bundleName0, artifacts*)
        case None =>
          val publishingType =
            if (shouldRelease) PublishingType.AUTOMATIC else PublishingType.USER_MANAGED
          Task.log.info(
            s"Publishing ${releases.length} release artifacts to Sonatype Central " +
              s"(publishing type = $publishingType)"
          )
          publisher(SonatypeCredentials(credentials.username, credentials.password))
            .publishAll(publishingType, bundleName0, artifacts*)
      }
    }

    if (snapshots.nonEmpty)
      localRepo0 match {
        case Some(dir) =>
          Task.fail(s"Writing snapshot versions to $dir is not supported")
        case None =>
          mavenPublishDatas(
            publishDatas = snapshots,
            credentials = credentials,
            releaseUri = snapshotUri,
            snapshotUri = snapshotUri,
            taskDest = Task.dest,
            log = Task.log,
            env = Task.env,
            worker = mavenWorker()
          )
      }
  }

  /** Generates a throw-away PGP key pair, and prints the value `MILL_PGP_SECRET_BASE64` needs for a
    * `publishSonatypeCentral --localRepo …` run
    */
  def generateTestPgpKey(userId: String = "Test <test@example.invalid>") = Task.Command[Unit] {
    val material = pgpWorker().generateKeyPair(userId, None)
    val secret   =
      java.util.Base64.getEncoder.encodeToString(material.secretKeyArmored.getBytes("UTF-8"))
    Task.log.streams.out.println(s"key id: ${material.keyIdHex}")
    Task.log.streams.out.println(secret)
  }
}
