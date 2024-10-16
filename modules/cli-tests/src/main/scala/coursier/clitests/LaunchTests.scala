package coursier.clitests

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

import coursier.cache.internal.FileUtil
import utest._

import scala.util.Properties

abstract class LaunchTests extends TestSuite with LauncherOptions {

  def launcher: String

  val tests = Tests {
    test("fork") {
      val output =
        os.proc(
          launcher,
          "launch",
          "--fork",
          "io.get-coursier:echo:1.0.1",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("non static main class") {
      val res =
        os.proc(
          launcher,
          "launch",
          "--fork",
          "org.scala-lang:scala-compiler:2.13.0",
          "--main-class",
          "scala.tools.nsc.Driver",
          "--property",
          "user.language=en",
          "--property",
          "user.country=US"
        ).call(
          mergeErrIntoOut = true,
          check = false
        )
      assert(res.exitCode != 0)
      val output = res.out.text()
      val expectedInOutput = Seq(
        "Main method",
        "in class scala.tools.nsc.Driver",
        "is not static"
      )
      assert(expectedInOutput.forall(output.contains))
    }

    test("java class path in expansion from launch") {
      import coursier.util.StringInterpolators._
      val output =
        os.proc(
          launcher,
          "launch",
          "--property",
          s"foo=$${java.class.path}",
          TestUtil.propsDepStr,
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expected = TestUtil.propsCp.mkString(File.pathSeparator) + System.lineSeparator()
      assert(output == expected)
    }

    def inlineApp(): Unit = {
      val output =
        os.proc(
          launcher,
          "launch",
          """{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
    test("inline app") {
      if (Properties.isWin) "disabled"
      else { inlineApp(); "" }
    }

    def inlineAppWithId(): Unit = {
      val output =
        os.proc(
          launcher,
          "launch",
          """echo:{"dependencies": ["io.get-coursier:echo:1.0.1"], "repositories": ["central"]}""",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expected = "foo" + System.lineSeparator()
      assert(output == expected)
    }
    test("inline app with id") {
      if (Properties.isWin) "disabled"
      else { inlineAppWithId(); "" }
    }

    test("no vendor and title in manifest") {
      val output =
        os.proc(
          launcher,
          "launch",
          "io.get-coursier:coursier-cli_2.12:2.0.16+69-g69cab05e6",
          "--",
          "launch",
          "io.get-coursier:echo:1.0.1",
          "--",
          "foo"
        )
          .call()
          .out.text()
      val expectedOutput = "foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("python") {
      val output =
        os.proc(
          launcher,
          "launch",
          "--python",
          "io.get-coursier:scalapy-echo_2.13:1.0.7",
          "--",
          "a",
          "b",
          "foo"
        )
          .call()
          .out.text()
      val expectedOutput = "a b foo" + System.lineSeparator()
      assert(output == expectedOutput)
    }

    test("extra jars") {
      if (Properties.isWin) "Disabled" // issues escaping the parameter ending in '\*'
      else extraJarsTest()
    }
    def extraJarsTest(): Unit = {
      val files = os.proc(launcher, "fetch", "org.scala-lang:scala3-compiler_3:3.1.3")
        .call()
        .out.lines()
        .map(os.Path(_, os.pwd))
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val dir    = tmpDir / "cp"
        for (f <- files)
          os.copy.into(f, dir, createFolders = true)
        val output = os.proc(
          launcher,
          "launch",
          "--extra-jars",
          if (Properties.isWin) {
            val q = "\""
            s"$q$dir/*$q"
          }
          else s"$dir/*",
          "-M",
          "dotty.tools.MainGenericCompiler"
        ).call(mergeErrIntoOut = true).out.lines()
        val expectedFirstLines = Seq(
          "Usage: scalac <options> <source files>",
          "where possible standard options include:"
        )
        assert(output.containsSlice(expectedFirstLines))
      }
    }

    test("extra jars with properties") {
      if (acceptsJOptions)
        extraJarsWithProperties()
      else
        "Disabled"
    }
    def extraJarsWithProperties(): Unit = {
      val files = os.proc(launcher, "fetch", "org.scala-lang:scala3-compiler_3:3.1.3")
        .call()
        .out.lines()
        .map(os.Path(_, os.pwd))
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val dir    = tmpDir / "cp"
        for (f <- files)
          os.copy.into(f, dir, createFolders = true)
        val output = os.proc(
          launcher,
          s"-J-Dthe.directory=$dir",
          "launch",
          "--extra-jars",
          s"$${the.directory}/*",
          "-M",
          "dotty.tools.MainGenericCompiler"
        ).call(mergeErrIntoOut = true).out.lines()
        val expectedFirstLines = Seq(
          "Usage: scalac <options> <source files>",
          "where possible standard options include:"
        )
        assert(output.containsSlice(expectedFirstLines))
      }
    }

    test("launch with hybrid launcher") {
      TestUtil.withTempDir("hybrid-test") { tmpDir =>
        val tmpDir0 = os.Path(tmpDir, os.pwd)

        def check(
          expectEntries: Seq[String],
          expectMissingEntries: Seq[String],
          extraCsArgs: Seq[String]
        ): Unit = {
          val workDir = tmpDir0 / "tmp"
          os.remove.all(workDir)
          os.proc(
            launcher,
            "launch",
            "sh.almond:::scala-kernel:0.13.6",
            "--shared",
            "sh.almond:::scala-kernel-api",
            "--scala",
            "2.12.17",
            "-r",
            "jitpack",
            "--hybrid",
            "--work-dir",
            workDir,
            extraCsArgs,
            "--",
            "--help"
          ).call(cwd = tmpDir0)
          val found = os.list(workDir)
          val hybridLauncher = found match {
            case Seq(f) => f
            case _ =>
              sys.error(s"Expected one file in work dir, got ${found.map(_.relativeTo(workDir))}")
          }
          val zf = new ZipFile(hybridLauncher.toIO)
          for (name <- expectEntries) {
            val found = zf.getEntry(name) != null
            assert(found)
          }
          for (name <- expectMissingEntries) {
            val missing = zf.getEntry(name) == null
            assert(missing)
          }
          zf.close()
        }

        check(
          Seq(
            "almond/display/PrettyPrint.class",
            "coursier/bootstrap/launcher/jars/scala-kernel_2.12.17-0.13.6.jar"
          ),
          Nil,
          Nil
        )

        check(
          Seq(
            "coursier/bootstrap/launcher/jars/scala-kernel_2.12.17-0.13.6.jar"
          ),
          Seq(
            "almond/display/PrettyPrint.class"
          ),
          Seq(
            "-R",
            "exclude:almond/display/PrettyPrint.class"
          )
        )
      }
    }

    test("launch with bootstrap launcher") {

      val expectedUrls = Vector(
        "sh/almond/scala-kernel_2.12.17/0.13.6/scala-kernel_2.12.17-0.13.6.jar",
        "com/github/alexarchambault/case-app_2.12/2.0.6/case-app_2.12-2.0.6.jar",
        "org/scalameta/scalafmt-dynamic_2.12/2.7.5/scalafmt-dynamic_2.12-2.7.5.jar",
        "org/scala-lang/scala-library/2.12.17/scala-library-2.12.17.jar",
        "sh/almond/kernel_2.12/0.13.6/kernel_2.12-0.13.6.jar",
        "sh/almond/scala-interpreter_2.12.17/0.13.6/scala-interpreter_2.12.17-0.13.6.jar",
        "com/github/alexarchambault/case-app-annotations_2.12/2.0.6/case-app-annotations_2.12-2.0.6.jar",
        "com/github/alexarchambault/case-app-util_2.12/2.0.6/case-app-util_2.12-2.0.6.jar",
        "org/scalameta/scalafmt-interfaces/2.7.5/scalafmt-interfaces-2.7.5.jar",
        "io/get-coursier/interface/1.0.14/interface-1.0.14.jar",
        "com/typesafe/config/1.4.0/config-1.4.0.jar",
        "org/scala-lang/modules/scala-collection-compat_2.12/2.9.0/scala-collection-compat_2.12-2.9.0.jar",
        "co/fs2/fs2-core_2.12/2.5.11/fs2-core_2.12-2.5.11.jar",
        "sh/almond/interpreter_2.12/0.13.6/interpreter_2.12-0.13.6.jar",
        "org/scalameta/metabrowse-server_2.12.17/0.2.9/metabrowse-server_2.12.17-0.2.9.jar",
        "io/get-coursier/coursier_2.12/2.1.0/coursier_2.12-2.1.0.jar",
        "io/github/soc/directories/12/directories-12.jar",
        "org/fusesource/jansi/jansi/2.4.0/jansi-2.4.0.jar",
        "com/lihaoyi/ammonite-compiler_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-compiler_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "com/lihaoyi/ammonite-repl_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-repl_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "sh/almond/scala-kernel-api_2.12.17/0.13.6/scala-kernel-api_2.12.17-0.13.6.jar",
        "com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar",
        "org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar",
        "org/typelevel/cats-core_2.12/2.6.1/cats-core_2.12-2.6.1.jar",
        "org/typelevel/cats-effect_2.12/2.5.4/cats-effect_2.12-2.5.4.jar",
        "org/scodec/scodec-bits_2.12/1.1.28/scodec-bits_2.12-1.1.28.jar",
        "com/lihaoyi/scalatags_2.12/0.12.0/scalatags_2.12-0.12.0.jar",
        "org/slf4j/slf4j-nop/2.0.7/slf4j-nop-2.0.7.jar",
        "sh/almond/interpreter-api_2.12/0.13.6/interpreter-api_2.12-0.13.6.jar",
        "sh/almond/protocol_2.12/0.13.6/protocol_2.12-0.13.6.jar",
        "org/scalameta/metabrowse-core_2.12/0.2.9/metabrowse-core_2.12-0.2.9.jar",
        "io/undertow/undertow-core/2.0.30.Final/undertow-core-2.0.30.Final.jar",
        "org/jboss/xnio/xnio-nio/3.8.0.Final/xnio-nio-3.8.0.Final.jar",
        "org/scalameta/semanticdb-scalac-core_2.12.17/4.6.0/semanticdb-scalac-core_2.12.17-4.6.0.jar",
        "org/scalameta/mtags_2.12.17/0.11.9/mtags_2.12.17-0.11.9.jar",
        "com/github/plokhotnyuk/jsoniter-scala/jsoniter-scala-core_2.12/2.13.5.2/jsoniter-scala-core_2.12-2.13.5.2.jar",
        "io/get-coursier/coursier-core_2.12/2.1.0/coursier-core_2.12-2.1.0.jar",
        "io/get-coursier/coursier-cache_2.12/2.1.0/coursier-cache_2.12-2.1.0.jar",
        "io/get-coursier/coursier-proxy-setup/2.1.0/coursier-proxy-setup-2.1.0.jar",
        "org/scala-lang/scala-compiler/2.12.17/scala-compiler-2.12.17.jar",
        "com/lihaoyi/scalaparse_2.12/3.0.0/scalaparse_2.12-3.0.0.jar",
        "org/scala-lang/modules/scala-xml_2.12/2.1.0/scala-xml_2.12-2.1.0.jar",
        "org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar",
        "com/github/javaparser/javaparser-core/3.2.5/javaparser-core-3.2.5.jar",
        "com/lihaoyi/ammonite-compiler-interface_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-compiler-interface_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "com/lihaoyi/ammonite-util_2.12/3.0.0-M0-14-c12b6a59/ammonite-util_2.12-3.0.0-M0-14-c12b6a59.jar",
        "com/lihaoyi/ammonite-repl-api_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-repl-api_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "org/jline/jline-terminal/3.14.1/jline-terminal-3.14.1.jar",
        "org/jline/jline-terminal-jna/3.14.1/jline-terminal-jna-3.14.1.jar",
        "org/jline/jline-reader/3.14.1/jline-reader-3.14.1.jar",
        "com/lihaoyi/ammonite-runtime_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-runtime_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "com/lihaoyi/ammonite-interp_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-interp_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "com/lihaoyi/ammonite-terminal_2.12/3.0.0-M0-14-c12b6a59/ammonite-terminal_2.12-3.0.0-M0-14-c12b6a59.jar",
        "https://jitpack.io/com/github/jupyter/jvm-repr/0.4.0/jvm-repr-0.4.0.jar",
        "sh/almond/jupyter-api_2.12/0.13.6/jupyter-api_2.12-0.13.6.jar",
        "org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar",
        "org/typelevel/cats-kernel_2.12/2.6.1/cats-kernel_2.12-2.6.1.jar",
        "org/typelevel/simulacrum-scalafix-annotations_2.12/0.5.4/simulacrum-scalafix-annotations_2.12-0.5.4.jar",
        "com/lihaoyi/sourcecode_2.12/0.3.0/sourcecode_2.12-0.3.0.jar",
        "com/lihaoyi/geny_2.12/1.0.0/geny_2.12-1.0.0.jar",
        "sh/almond/channels_2.12/0.13.6/channels_2.12-0.13.6.jar",
        "com/thesamet/scalapb/scalapb-runtime_2.12/0.11.11/scalapb-runtime_2.12-0.11.11.jar",
        "org/scalameta/scalameta_2.12/4.6.0/scalameta_2.12-4.6.0.jar",
        "org/jboss/logging/jboss-logging/3.4.1.Final/jboss-logging-3.4.1.Final.jar",
        "org/jboss/xnio/xnio-api/3.8.0.Final/xnio-api-3.8.0.Final.jar",
        "org/scalameta/mtags-interfaces/0.11.9/mtags-interfaces-0.11.9.jar",
        "com/thoughtworks/qdox/qdox/2.0.2/qdox-2.0.2.jar",
        "org/scala-lang/modules/scala-java8-compat_2.12/1.0.2/scala-java8-compat_2.12-1.0.2.jar",
        "org/jsoup/jsoup/1.15.3/jsoup-1.15.3.jar",
        "com/googlecode/java-diff-utils/diffutils/1.3.0/diffutils-1.3.0.jar",
        "org/lz4/lz4-java/1.8.0/lz4-java-1.8.0.jar",
        "io/github/alexarchambault/concurrent-reference-hash-map/1.1.0/concurrent-reference-hash-map-1.1.0.jar",
        "io/get-coursier/coursier-util_2.12/2.1.0/coursier-util_2.12-2.1.0.jar",
        "io/get-coursier/jniutils/windows-jni-utils/0.3.3/windows-jni-utils-0.3.3.jar",
        "org/codehaus/plexus/plexus-archiver/4.6.2/plexus-archiver-4.6.2.jar",
        "org/codehaus/plexus/plexus-container-default/2.1.1/plexus-container-default-2.1.1.jar",
        "org/virtuslab/scala-cli/config_2.12/0.2.0/config_2.12-0.2.0.jar",
        "io/github/alexarchambault/windows-ansi/windows-ansi/0.0.4/windows-ansi-0.0.4.jar",
        "org/scala-lang/scala-reflect/2.12.17/scala-reflect-2.12.17.jar",
        "com/lihaoyi/fastparse_2.12/3.0.0/fastparse_2.12-3.0.0.jar",
        "com/lihaoyi/os-lib_2.12/0.9.0/os-lib_2.12-0.9.0.jar",
        "com/lihaoyi/fansi_2.12/0.4.0/fansi_2.12-0.4.0.jar",
        "com/lihaoyi/pprint_2.12/0.8.1/pprint_2.12-0.8.1.jar",
        "com/lihaoyi/mainargs_2.12/0.3.0/mainargs_2.12-0.3.0.jar",
        "com/lihaoyi/ammonite-interp-api_2.12.17/3.0.0-M0-14-c12b6a59/ammonite-interp-api_2.12.17-3.0.0-M0-14-c12b6a59.jar",
        "com/lihaoyi/upickle_2.12/3.0.0/upickle_2.12-3.0.0.jar",
        "com/lihaoyi/requests_2.12/0.7.0/requests_2.12-0.7.0.jar",
        "ch/epfl/scala/bsp4j/2.0.0-M6/bsp4j-2.0.0-M6.jar",
        "org/scalameta/trees_2.12/4.6.0/trees_2.12-4.6.0.jar",
        "org/zeromq/jeromq/0.5.3/jeromq-0.5.3.jar",
        "sh/almond/logger_2.12/0.13.6/logger_2.12-0.13.6.jar",
        "com/thesamet/scalapb/lenses_2.12/0.11.11/lenses_2.12-0.11.11.jar",
        "com/google/protobuf/protobuf-java/3.19.2/protobuf-java-3.19.2.jar",
        "org/scalameta/parsers_2.12/4.6.0/parsers_2.12-4.6.0.jar",
        "org/scala-lang/scalap/2.12.17/scalap-2.12.17.jar",
        "org/wildfly/common/wildfly-common/1.5.2.Final/wildfly-common-1.5.2.Final.jar",
        "org/wildfly/client/wildfly-client-config/1.0.1.Final/wildfly-client-config-1.0.1.Final.jar",
        "org/jboss/threads/jboss-threads/2.3.3.Final/jboss-threads-2.3.3.Final.jar",
        "org/eclipse/lsp4j/org.eclipse.lsp4j/0.15.0/org.eclipse.lsp4j-0.15.0.jar",
        "javax/inject/javax.inject/1/javax.inject-1.jar",
        "org/codehaus/plexus/plexus-utils/3.5.0/plexus-utils-3.5.0.jar",
        "org/codehaus/plexus/plexus-io/3.4.1/plexus-io-3.4.1.jar",
        "commons-io/commons-io/2.11.0/commons-io-2.11.0.jar",
        "org/apache/commons/commons-compress/1.22/commons-compress-1.22.jar",
        "org/iq80/snappy/snappy/0.4/snappy-0.4.jar",
        "org/tukaani/xz/1.9/xz-1.9.jar",
        "com/github/luben/zstd-jni/1.5.4-2/zstd-jni-1.5.4-2.jar",
        "org/codehaus/plexus/plexus-classworlds/2.6.0/plexus-classworlds-2.6.0.jar",
        "org/apache/xbean/xbean-reflect/3.7/xbean-reflect-3.7.jar",
        "com/lihaoyi/ujson_2.12/3.0.0/ujson_2.12-3.0.0.jar",
        "com/lihaoyi/upack_2.12/3.0.0/upack_2.12-3.0.0.jar",
        "com/lihaoyi/upickle-implicits_2.12/3.0.0/upickle-implicits_2.12-3.0.0.jar",
        "org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.15.0/org.eclipse.lsp4j.generator-0.15.0.jar",
        "org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.15.0/org.eclipse.lsp4j.jsonrpc-0.15.0.jar",
        "org/scalameta/common_2.12/4.6.0/common_2.12-4.6.0.jar",
        "org/scalameta/fastparse-v2_2.12/2.3.1/fastparse-v2_2.12-2.3.1.jar",
        "eu/neilalexander/jnacl/1.0.0/jnacl-1.0.0.jar",
        "sh/almond/logger-scala2-macros_2.12/0.13.6/logger-scala2-macros_2.12-0.13.6.jar",
        "com/lihaoyi/upickle-core_2.12/3.0.0/upickle-core_2.12-3.0.0.jar",
        "org/eclipse/xtend/org.eclipse.xtend.lib/2.24.0/org.eclipse.xtend.lib-2.24.0.jar",
        "com/google/code/gson/gson/2.9.1/gson-2.9.1.jar",
        "org/eclipse/xtext/org.eclipse.xtext.xbase.lib/2.24.0/org.eclipse.xtext.xbase.lib-2.24.0.jar",
        "org/eclipse/xtend/org.eclipse.xtend.lib.macro/2.24.0/org.eclipse.xtend.lib.macro-2.24.0.jar",
        "com/google/guava/guava/27.1-jre/guava-27.1-jre.jar",
        "com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar",
        "com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
        "com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
        "org/checkerframework/checker-qual/2.5.2/checker-qual-2.5.2.jar",
        "com/google/errorprone/error_prone_annotations/2.2.0/error_prone_annotations-2.2.0.jar",
        "com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar",
        "org/codehaus/mojo/animal-sniffer-annotations/1.17/animal-sniffer-annotations-1.17.jar"
      ).map(s => if (s.startsWith("https://")) s else "https://repo1.maven.org/maven2/" + s)

      TestUtil.withTempDir("hybrid-test") { tmpDir =>
        val tmpDir0 = os.Path(tmpDir, os.pwd)

        val workDir = tmpDir0 / "tmp"
        os.remove.all(workDir)
        os.proc(
          launcher,
          "launch",
          "sh.almond:::scala-kernel:0.13.6",
          "--scala",
          "2.12.17",
          "-r",
          "jitpack",
          "--use-bootstrap",
          "--work-dir",
          workDir,
          "--",
          "--help"
        ).call(cwd = tmpDir0)
        val found = os.list(workDir)
        val hybridLauncher = found match {
          case Seq(f) => f
          case _ =>
            sys.error(s"Expected one file in work dir, got ${found.map(_.relativeTo(workDir))}")
        }
        var zf: ZipFile = null
        try {
          zf = new ZipFile(hybridLauncher.toIO)

          assert(zf.getEntry("coursier/bootstrap/launcher/Launcher.class") != null)

          val urls = {
            val b = FileUtil.readFully(
              zf.getInputStream(zf.getEntry("coursier/bootstrap/launcher/bootstrap-jar-urls"))
            )
            val s = new String(b, StandardCharsets.UTF_8)
            s.linesIterator.filter(_.nonEmpty).toVector
          }
          if (urls != expectedUrls)
            pprint.err.log(urls)
          assert(urls == expectedUrls)
        }
        finally
          if (zf != null)
            zf.close()
        ()
      }
    }
  }
}
