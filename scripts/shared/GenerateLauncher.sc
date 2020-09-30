
import $file.Util

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Paths}
import java.util.jar.{Attributes => JarAttributes}
import java.io.File
import java.nio.file.Path

val useNativeImageBinary = true

// ModuleFinder.ofSystem().find("jdk.internal.vm.compiler").get().descriptor.packages.asScala.toVector.sorted
private val vmCompilerPackages = Vector(
  "org.graalvm.compiler.api.directives",
  "org.graalvm.compiler.api.replacements",
  "org.graalvm.compiler.api.runtime",
  "org.graalvm.compiler.asm",
  "org.graalvm.compiler.asm.aarch64",
  "org.graalvm.compiler.asm.amd64",
  "org.graalvm.compiler.asm.sparc",
  "org.graalvm.compiler.bytecode",
  "org.graalvm.compiler.code",
  "org.graalvm.compiler.core",
  "org.graalvm.compiler.core.aarch64",
  "org.graalvm.compiler.core.amd64",
  "org.graalvm.compiler.core.common",
  "org.graalvm.compiler.core.common.alloc",
  "org.graalvm.compiler.core.common.calc",
  "org.graalvm.compiler.core.common.cfg",
  "org.graalvm.compiler.core.common.spi",
  "org.graalvm.compiler.core.common.type",
  "org.graalvm.compiler.core.common.util",
  "org.graalvm.compiler.core.gen",
  "org.graalvm.compiler.core.match",
  "org.graalvm.compiler.core.phases",
  "org.graalvm.compiler.core.sparc",
  "org.graalvm.compiler.core.target",
  "org.graalvm.compiler.debug",
  "org.graalvm.compiler.graph",
  "org.graalvm.compiler.graph.iterators",
  "org.graalvm.compiler.graph.spi",
  "org.graalvm.compiler.hotspot",
  "org.graalvm.compiler.hotspot.aarch64",
  "org.graalvm.compiler.hotspot.amd64",
  "org.graalvm.compiler.hotspot.debug",
  "org.graalvm.compiler.hotspot.lir",
  "org.graalvm.compiler.hotspot.meta",
  "org.graalvm.compiler.hotspot.nodes",
  "org.graalvm.compiler.hotspot.nodes.aot",
  "org.graalvm.compiler.hotspot.nodes.profiling",
  "org.graalvm.compiler.hotspot.nodes.type",
  "org.graalvm.compiler.hotspot.phases",
  "org.graalvm.compiler.hotspot.phases.aot",
  "org.graalvm.compiler.hotspot.phases.profiling",
  "org.graalvm.compiler.hotspot.replacements",
  "org.graalvm.compiler.hotspot.replacements.aot",
  "org.graalvm.compiler.hotspot.replacements.arraycopy",
  "org.graalvm.compiler.hotspot.replacements.profiling",
  "org.graalvm.compiler.hotspot.sparc",
  "org.graalvm.compiler.hotspot.stubs",
  "org.graalvm.compiler.hotspot.word",
  "org.graalvm.compiler.java",
  "org.graalvm.compiler.lir",
  "org.graalvm.compiler.lir.aarch64",
  "org.graalvm.compiler.lir.alloc",
  "org.graalvm.compiler.lir.alloc.lsra",
  "org.graalvm.compiler.lir.alloc.lsra.ssa",
  "org.graalvm.compiler.lir.amd64",
  "org.graalvm.compiler.lir.amd64.phases",
  "org.graalvm.compiler.lir.amd64.vector",
  "org.graalvm.compiler.lir.asm",
  "org.graalvm.compiler.lir.constopt",
  "org.graalvm.compiler.lir.debug",
  "org.graalvm.compiler.lir.dfa",
  "org.graalvm.compiler.lir.framemap",
  "org.graalvm.compiler.lir.gen",
  "org.graalvm.compiler.lir.hashing",
  "org.graalvm.compiler.lir.phases",
  "org.graalvm.compiler.lir.profiling",
  "org.graalvm.compiler.lir.sparc",
  "org.graalvm.compiler.lir.ssa",
  "org.graalvm.compiler.lir.stackslotalloc",
  "org.graalvm.compiler.lir.util",
  "org.graalvm.compiler.loop",
  "org.graalvm.compiler.loop.phases",
  "org.graalvm.compiler.nodeinfo",
  "org.graalvm.compiler.nodes",
  "org.graalvm.compiler.nodes.calc",
  "org.graalvm.compiler.nodes.cfg",
  "org.graalvm.compiler.nodes.debug",
  "org.graalvm.compiler.nodes.extended",
  "org.graalvm.compiler.nodes.gc",
  "org.graalvm.compiler.nodes.graphbuilderconf",
  "org.graalvm.compiler.nodes.java",
  "org.graalvm.compiler.nodes.memory",
  "org.graalvm.compiler.nodes.memory.address",
  "org.graalvm.compiler.nodes.spi",
  "org.graalvm.compiler.nodes.type",
  "org.graalvm.compiler.nodes.util",
  "org.graalvm.compiler.nodes.virtual",
  "org.graalvm.compiler.options",
  "org.graalvm.compiler.phases",
  "org.graalvm.compiler.phases.common",
  "org.graalvm.compiler.phases.common.inlining",
  "org.graalvm.compiler.phases.common.inlining.info",
  "org.graalvm.compiler.phases.common.inlining.info.elem",
  "org.graalvm.compiler.phases.common.inlining.policy",
  "org.graalvm.compiler.phases.common.inlining.walker",
  "org.graalvm.compiler.phases.common.jmx",
  "org.graalvm.compiler.phases.common.util",
  "org.graalvm.compiler.phases.contract",
  "org.graalvm.compiler.phases.graph",
  "org.graalvm.compiler.phases.schedule",
  "org.graalvm.compiler.phases.tiers",
  "org.graalvm.compiler.phases.util",
  "org.graalvm.compiler.printer",
  "org.graalvm.compiler.replacements",
  "org.graalvm.compiler.replacements.aarch64",
  "org.graalvm.compiler.replacements.amd64",
  "org.graalvm.compiler.replacements.arraycopy",
  "org.graalvm.compiler.replacements.classfile",
  "org.graalvm.compiler.replacements.gc",
  "org.graalvm.compiler.replacements.nodes",
  "org.graalvm.compiler.replacements.nodes.arithmetic",
  "org.graalvm.compiler.replacements.sparc",
  "org.graalvm.compiler.runtime",
  "org.graalvm.compiler.serviceprovider",
  "org.graalvm.compiler.truffle.common",
  "org.graalvm.compiler.truffle.common.hotspot",
  "org.graalvm.compiler.truffle.common.hotspot.libgraal",
  "org.graalvm.compiler.truffle.compiler",
  "org.graalvm.compiler.truffle.compiler.amd64.substitutions",
  "org.graalvm.compiler.truffle.compiler.debug",
  "org.graalvm.compiler.truffle.compiler.hotspot",
  "org.graalvm.compiler.truffle.compiler.hotspot.aarch64",
  "org.graalvm.compiler.truffle.compiler.hotspot.amd64",
  "org.graalvm.compiler.truffle.compiler.hotspot.sparc",
  "org.graalvm.compiler.truffle.compiler.nodes",
  "org.graalvm.compiler.truffle.compiler.nodes.asserts",
  "org.graalvm.compiler.truffle.compiler.nodes.frame",
  "org.graalvm.compiler.truffle.compiler.phases",
  "org.graalvm.compiler.truffle.compiler.phases.inlining",
  "org.graalvm.compiler.truffle.compiler.substitutions",
  "org.graalvm.compiler.truffle.jfr",
  "org.graalvm.compiler.truffle.options",
  "org.graalvm.compiler.truffle.runtime",
  "org.graalvm.compiler.truffle.runtime.debug",
  "org.graalvm.compiler.truffle.runtime.hotspot",
  "org.graalvm.compiler.truffle.runtime.hotspot.java",
  "org.graalvm.compiler.truffle.runtime.hotspot.libgraal",
  "org.graalvm.compiler.truffle.runtime.serviceprovider",
  "org.graalvm.compiler.virtual.nodes",
  "org.graalvm.compiler.virtual.phases.ea",
  "org.graalvm.compiler.word",
  "org.graalvm.graphio",
  "org.graalvm.libgraal",
  "org.graalvm.util"
)

/**
 * Generates a native-image for module `module`.
 *
 * @param coursierLauncher Path to a coursier launcher to fetch `module` classpath and native-image
 * @param module A module to fetch, like `"org:name:version"`
 * @param extraArgs Extra arguments to pass to coursier to fetch `module`, e.g. `Seq("-r", "sonatype:snapshots")`
 * @param output Path to the native image to generate
 * @param mainClass Main class that the native image should start
 */
def nativeImage(
  coursierLauncher: String,
  module: String,
  extraArgs: Seq[String],
  output: String,
  mainClass: String, // FIXME Get from cp / manifest
  useAssembly: Boolean = false,
  extraNativeImageOpts: Seq[String] = Nil
): Unit = {

  val cp =
    if (useAssembly) {

      val tempFile = Files.createTempFile("assembly-", ".jar")

      Runtime.getRuntime().addShutdownHook(
        new Thread {
          override def run(): Unit =
            Files.deleteIfExists(tempFile)
        }
      )

      val assemblyPath = tempFile.toAbsolutePath.toString

      val assemblyCmd = Seq(
        coursierLauncher,
        "bootstrap",
        "--assembly",
        "--preamble=false",
        "-o", assemblyPath,
        "-f",
        module
      ) ++ extraArgs

      Util.run(assemblyCmd)
      assemblyPath
    } else {
      val cpCmd = Seq(
        coursierLauncher,
        "fetch",
        "--classpath",
        module
      ) ++ extraArgs

      Util.output(cpCmd).trim
    }

  val mem =
    if (Util.os == "linux") "3584m"
    else "3g"

  val graalvmVer = if (Util.os == "win") "20.1.0" else "20.1.0"

  val addExportsOptions =
    vmCompilerPackages.flatMap { pack =>
      Seq("--java-opt", "--add-exports", "--java-opt", s"jdk.internal.vm.compiler/$pack=ALL-UNNAMED")
    }

  lazy val addExportsBaseManifestOptions = {
    val m = new java.util.jar.Manifest
    val value = vmCompilerPackages
      .map(p => s"jdk.internal.vm.compiler/$p")
      .mkString(" ")
    m.getMainAttributes.put(JarAttributes.Name.MANIFEST_VERSION, "1.0")
    m.getMainAttributes.put(new JarAttributes.Name("Add-Exports"), value)
    val baos = new ByteArrayOutputStream
    m.write(baos)
    Files.write(Paths.get("base-manifest.mf"), baos.toByteArray)
    Seq("--base-manifest", "base-manifest.mf")
  }



  def run(extraNativeImageOpts: Seq[String], extraCsLaunchOpts: Seq[String] = Nil): Unit = {

    val baseCommand =
      if (useNativeImageBinary) {
        val pathExts = if (Util.os == "win") Option(System.getenv("PATHEXT")).toSeq.flatMap(_.split(File.pathSeparator).toSeq) else Seq("")

        def executableOpt(p: Path): Option[Path] =
          pathExts
            .iterator
            .map {
              case "" => p
              case ext => p.getParent.resolve(p.getFileName.toString + ext)
            }
            .filter(p => Files.isExecutable(p))
            .toStream
            .headOption

        val home = Option(System.getenv("JAVA_HOME")).orElse(sys.props.get("java.home")).getOrElse {
          sys.error("Could not get Java home")
        }
        val nativeImagePath = executableOpt(Paths.get(home).resolve("bin/native-image")).getOrElse {
          val gu = executableOpt(Paths.get(home).resolve("bin/gu")).getOrElse {
            sys.error("gu not found under $JAVA_HOME/bin")
          }
          Util.run(Seq(gu.toString, "install", "native-image"))
          executableOpt(Paths.get(home).resolve("bin/native-image")).getOrElse {
            sys.error("native-image not found under $JAVA_HOME/bin after having run 'gu install native-image'")
          }
        }
        Seq(nativeImagePath.toString)
      } else if (useAssembly) {
        Util.run(
          Seq(coursierLauncher, "bootstrap", "-o", "native-image", "--assembly", "-f") ++
            extraCsLaunchOpts ++
            addExportsBaseManifestOptions ++
            Seq(s"org.graalvm.nativeimage:svm-driver:$graalvmVer")
        )
        Seq("./native-image")
      } else
        Seq(coursierLauncher, "launch") ++
          extraCsLaunchOpts ++
          addExportsOptions ++
          Seq(s"org.graalvm.nativeimage:svm-driver:$graalvmVer", "--")

    val cmd = baseCommand ++
      Seq("-cp", cp) ++
      extraNativeImageOpts ++
      Seq(mainClass, output)

    System.err.println("Running " + cmd.mkString(" "))

    Util.run(cmd, Seq("JAVA_OPTS" -> s"-Xmx$mem"))
  }

  val extraCsOpts = Seq("--java-opt", s"-Xmx$mem")
  if (Util.os == "win") {
    // getting weird TLS-related linking errors without this
    val javaSecurityOverrides =
      """security.provider.3=what.we.put.here.doesnt.matter.ButThisHasToBeOverridden
        |""".stripMargin.getBytes
    Util.withTmpFile("java.security.overrides-", ".properties", javaSecurityOverrides) { path =>
      run(s"-J-Djava.security.properties=$path" +: extraNativeImageOpts, extraCsOpts)
    }
  } else if (Util.os == "linux" && coursierLauncher.endsWith("cs"))
    run(extraNativeImageOpts, extraCsOpts)
  else
    run(extraNativeImageOpts)
}

/**
 * Generates a launcher (using the bootstrap command of coursier).
 *
 * @param coursierLauncher Path to a coursier launcher
 * @param module Module to be launched, like `"org:name:version"`
 * @param extraArgs Extra arguments to pass to coursier to fetch `module`, e.g. `Seq("-r", "sonatype:snapshots")`
 * @param output Path to the launcher to generate
 * @param forceBat Whether to force generating a `.bat` file along with the launcher
 *
 */
def apply(
  coursierLauncher: String,
  module: String,
  extraArgs: Seq[String],
  output: String,
  forceBat: Boolean = false
): Unit = {

  var cmd = Seq(coursierLauncher, "bootstrap", module) ++
    extraArgs ++
    Seq("-f", "-o", output)

  if (forceBat)
    cmd = cmd ++ Seq("--bat=true")

  Util.run(cmd)
}
