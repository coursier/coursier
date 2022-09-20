package coursier.cli.params

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.fetch.FetchParams
import coursier.cli.install.SharedChannelParams
import coursier.cli.options.SharedLaunchOptions
import coursier.cli.resolve.SharedResolveParams

final case class SharedLaunchParams(
  resolve: SharedResolveParams,
  artifact: ArtifactParams,
  sharedLoader: SharedLoaderParams,
  mainClassOpt: Option[String],
  javaOptions: Seq[String],
  properties: Seq[(String, String)],
  extraJars: Seq[Path],
  pythonJepOpt: Option[Boolean]
) {
  def fetch(channel: SharedChannelParams): FetchParams =
    FetchParams(
      classpath = false,
      jsonOutputOpt = None,
      resolve = resolve,
      artifact = artifact,
      channel = channel
    )

  def pythonJep = pythonJepOpt.getOrElse(false)
}

object SharedLaunchParams {

  // Default ClassLoader setup with Java 9 doesn't allow for
  // proper isolation between the coursier class path and the
  // class path of the launched application, so we fork by
  // default to avoid clashes.
  private def isAtLeastJava9: Boolean =
    sys.props
      .get("java.version")
      .map(_.takeWhile(_ != '.'))
      .filter(s => s.nonEmpty && s.forall(_.isDigit))
      .map(_.toInt)
      .exists(_ >= 9)

  // Can't dynamically load random stuff from the native launcher
  private def isNativeImage: Boolean =
    sys.props
      .get("org.graalvm.nativeimage.imagecode")
      .contains("runtime")

  def defaultFork: Boolean =
    isNativeImage || isAtLeastJava9

  def apply(options: SharedLaunchOptions): ValidatedNel[String, SharedLaunchParams] = {

    val resolveV  = SharedResolveParams(options.resolveOptions)
    val artifactV = ArtifactParams(options.artifactOptions)
    val sharedLoaderV = resolveV.map(_.resolution).toOption match {
      case None =>
        Validated.validNel(SharedLoaderParams(Nil, Map.empty))
      case Some(resolutionOpts) =>
        SharedLoaderParams.from(options.sharedLoaderOptions)
    }

    val mainClassOpt = Some(options.mainClass).filter(_.nonEmpty)

    val propertiesV = options.property.traverse { s =>
      val idx = s.indexOf('=')
      if (idx < 0)
        Validated.invalidNel(s"Malformed property argument '$s' (expected name=value)")
      else
        Validated.validNel(s.substring(0, idx) -> s.substring(idx + 1))
    }

    // check if those exist?
    val extraJars = options.extraJars.map { p =>
      Paths.get(p)
    }

    (resolveV, artifactV, sharedLoaderV, propertiesV).mapN {
      (resolve, artifact, sharedLoader, properties) =>
        SharedLaunchParams(
          resolve,
          artifact,
          sharedLoader,
          mainClassOpt,
          options.javaOpt,
          properties,
          extraJars,
          options.pythonJep
        )
    }
  }
}
