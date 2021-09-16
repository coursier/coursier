
import $file.deps, deps.jvmIndex

import java.io.File

import scala.util.Properties

private lazy val vcvarsCandidates = Option(System.getenv("VCVARSALL")) ++ Seq(
  """C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise\VC\Auxiliary\Build\vcvars64.bat""",
  """C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat""",
  """C:\Program Files (x86)\Microsoft Visual Studio\2017\Enterprise\VC\Auxiliary\Build\vcvars64.bat""",
  """C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\VC\Auxiliary\Build\vcvars64.bat"""
)

private def vcvarsOpt: Option[os.Path] =
  vcvarsCandidates
    .iterator
    .map(os.Path(_, os.pwd))
    .filter(os.exists(_))
    .toStream
    .headOption

def generateNativeImage(
  graalVmVersion: String,
  classPath: Seq[os.Path],
  mainClass: String,
  dest: os.Path
): Unit = {

  val graalVmHome = Option(System.getenv("GRAALVM_HOME")).getOrElse {
    import sys.process._
    Seq(cs.cs, "java-home", "--jvm", s"graalvm-java11:$graalVmVersion", "--jvm-index", jvmIndex).!!.trim
  }

  val ext = if (Properties.isWin) ".cmd" else ""
  val nativeImage = s"$graalVmHome/bin/native-image$ext"

  if (!os.isFile(os.Path(nativeImage))) {
    val ret = os.proc(s"$graalVmHome/bin/gu$ext", "install", "native-image").call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    if (ret.exitCode != 0)
      System.err.println(s"Warning: 'gu install native-image' exited with return code ${ret.exitCode}}")
    if (!os.isFile(os.Path(nativeImage)))
      System.err.println(s"Warning: $nativeImage not found, and not installed by 'gu install native-image'")
  }

  val finalCp =
    if (Properties.isWin) {
      import java.util.jar._
      val manifest = new Manifest
      val attributes = manifest.getMainAttributes
      attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
      attributes.put(Attributes.Name.CLASS_PATH, classPath.map(_.toIO.getAbsolutePath).mkString(" "))
      val jarFile = File.createTempFile("classpathJar", ".jar")
      val jos = new JarOutputStream(new java.io.FileOutputStream(jarFile), manifest)
      jos.close()
      jarFile.getAbsolutePath
    } else
      classPath.map(_.toIO.getAbsolutePath).mkString(File.pathSeparator)

  val extraArgs =
    if (Properties.isWin)
      Seq(
        "--no-server",
        "-J-Xmx6g",
        "--verbose"
      )
    else Nil

  val command = Seq(
    nativeImage,
    "--no-fallback"
  ) ++
  extraArgs ++
  Seq(
    "--enable-url-protocols=https",
    "--initialize-at-build-time=scala.Symbol",
    "--initialize-at-build-time=scala.Symbol$",
    "--initialize-at-build-time=scala.Function1",
    "--initialize-at-build-time=scala.Function2",
    "--initialize-at-build-time=scala.runtime.LambdaDeserialize",
    "--initialize-at-build-time=scala.runtime.EmptyMethodCache",
    "--initialize-at-build-time=scala.runtime.StructuralCallSite",
    "--initialize-at-build-time=scala.collection.immutable.VM",
    "--initialize-at-build-time=com.google.common.jimfs.SystemJimfsFileSystemProvider",
    "-H:IncludeResources=amm-dependencies.txt",
    "-H:IncludeResources=bootstrap.*.jar",
    "-H:IncludeResources=coursier/coursier.properties",
    "-H:IncludeResources=coursier/launcher/coursier.properties",
    "-H:IncludeResources=coursier/launcher/.*.bat",
    "-H:IncludeResources=org/scalajs/linker/backend/emitter/.*.sjsir"
  ) ++
  Seq(
    "--allow-incomplete-classpath",
    "--report-unsupported-elements-at-runtime",
    "-H:+ReportExceptionStackTraces",
    s"-H:Name=${dest.relativeTo(os.pwd)}",
    "-cp",
    finalCp,
    mainClass
  )

  val finalCommand =
    if (Properties.isWin)
      vcvarsOpt match {
        case None =>
          System.err.println(s"Warning: vcvarsall script not found in predefined locations:")
          for (loc <- vcvarsCandidates)
            System.err.println(s"  $loc")
          command
        case Some(vcvars) =>
          // chcp 437 sometimes needed, see https://github.com/oracle/graal/issues/2522
          val escapedCommand = command.map {
            case s if s.contains(" ") => "\"" + s + "\""
            case s => s
          }
          val script =
           s"""chcp 437
              |@call "$vcvars"
              |if %errorlevel% neq 0 exit /b %errorlevel%
              |@call ${escapedCommand.mkString(" ")}
              |""".stripMargin
          val scriptPath = os.temp(script.getBytes, prefix = "run-native-image", suffix = ".bat")
          Seq(scriptPath.toString)
      }
    else
      command

  val res = os.proc(finalCommand.map(x => x: os.Shellable): _*).call(
    stdin = os.Inherit,
    stdout = os.Inherit,
    stderr = os.Inherit
  )
  if (res.exitCode != 0)
    sys.error(s"native-image command exited with ${res.exitCode}")
}
