package coursier.cli

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

import caseapp.core.RemainingArgs
import cats.data.Validated
import coursier.cli.options.{DependencyOptions, OutputOptions, ResolutionOptions}
import coursier.cli.resolve.{Resolve, ResolveException, ResolveOptions, ResolveParams, SharedResolveOptions}
import coursier.util.Sync
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.ExecutionContext

@RunWith(classOf[JUnitRunner])
class ResolveTests extends AnyFlatSpec with BeforeAndAfterAll {

  val pool = Sync.fixedThreadPool(6)
  val ec = ExecutionContext.fromExecutorService(pool)

  override protected def afterAll(): Unit = {
    pool.shutdown()
  }

  def paramsOrThrow(options: SharedResolveOptions): ResolveParams =
    paramsOrThrow(ResolveOptions(sharedResolveOptions = options))
  def paramsOrThrow(options: ResolveOptions): ResolveParams =
    ResolveParams(options) match {
      case Validated.Invalid(errors) =>
        sys.error("Got errors:\n" + errors.toList.map(e => s"  $e\n").mkString)
      case Validated.Valid(params0) =>
        params0
    }


  it should "print what depends on" in {
    val options = ResolveOptions(
      whatDependsOn = List("org.htrace:htrace-core")
    )
    val args = RemainingArgs(Seq("org.apache.spark:spark-sql_2.12:2.4.0"), Nil)

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    Resolve.printTask(params, pool, new PrintStream(stdout, true, "UTF-8"), System.err, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    val expectedOutput =
      """└─ org.htrace:htrace-core:3.0.4
        |   ├─ org.apache.hadoop:hadoop-common:2.6.5
        |   │  └─ org.apache.hadoop:hadoop-client:2.6.5
        |   │     └─ org.apache.spark:spark-core_2.12:2.4.0
        |   │        ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
        |   │        │  └─ org.apache.spark:spark-sql_2.12:2.4.0
        |   │        └─ org.apache.spark:spark-sql_2.12:2.4.0
        |   └─ org.apache.hadoop:hadoop-hdfs:2.6.5
        |      └─ org.apache.hadoop:hadoop-client:2.6.5
        |         └─ org.apache.spark:spark-core_2.12:2.4.0
        |            ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
        |            │  └─ org.apache.spark:spark-sql_2.12:2.4.0
        |            └─ org.apache.spark:spark-sql_2.12:2.4.0
        |""".stripMargin

    assert(output === expectedOutput)
  }

  it should "print what depends on with regex" in {
    val options = ResolveOptions(
      whatDependsOn = List("*:htrace-*")
    )
    val args = RemainingArgs(Seq("org.apache.spark:spark-sql_2.12:2.4.0"), Nil)

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    Resolve.printTask(params, pool, new PrintStream(stdout, true, "UTF-8"), System.err, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    val expectedOutput =
      """└─ org.htrace:htrace-core:3.0.4
        |   ├─ org.apache.hadoop:hadoop-common:2.6.5
        |   │  └─ org.apache.hadoop:hadoop-client:2.6.5
        |   │     └─ org.apache.spark:spark-core_2.12:2.4.0
        |   │        ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
        |   │        │  └─ org.apache.spark:spark-sql_2.12:2.4.0
        |   │        └─ org.apache.spark:spark-sql_2.12:2.4.0
        |   └─ org.apache.hadoop:hadoop-hdfs:2.6.5
        |      └─ org.apache.hadoop:hadoop-client:2.6.5
        |         └─ org.apache.spark:spark-core_2.12:2.4.0
        |            ├─ org.apache.spark:spark-catalyst_2.12:2.4.0
        |            │  └─ org.apache.spark:spark-sql_2.12:2.4.0
        |            └─ org.apache.spark:spark-sql_2.12:2.4.0
        |""".stripMargin

    assert(output === expectedOutput)
  }

  it should "print results anyway" in {
    val options = ResolveOptions(
      forcePrint = true
    )
    val args = RemainingArgs(
      Seq("ioi.get-coursier:coursier-core_2.12:1.1.0-M9", "io.get-coursier:coursier-cache_2.12:1.1.0-M9"),
      Nil
    )

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    val ps = new PrintStream(stdout, true, "UTF-8")
    Resolve.printTask(params, pool, ps, ps, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
      .replace(sys.props("user.home"), "HOME")
    val expectedOutput =
      """Error downloading ioi.get-coursier:coursier-core_2.12:1.1.0-M9
        |  not found: HOME/.ivy2/local/ioi.get-coursier/coursier-core_2.12/1.1.0-M9/ivys/ivy.xml
        |  not found: https://repo1.maven.org/maven2/ioi/get-coursier/coursier-core_2.12/1.1.0-M9/coursier-core_2.12-1.1.0-M9.pom
        |io.get-coursier:coursier-cache_2.12:1.1.0-M9:default
        |io.get-coursier:coursier-core_2.12:1.1.0-M9:default
        |ioi.get-coursier:coursier-core_2.12:1.1.0-M9:default(compile)
        |org.scala-lang:scala-library:2.12.7:default
        |org.scala-lang.modules:scala-xml_2.12:1.1.0:default
        |""".stripMargin

    assert(output === expectedOutput)
  }

  it should "resolve sbt plugins" in {
    val options = SharedResolveOptions(
      dependencyOptions = DependencyOptions(
        sbtPlugin = List(
          "io.get-coursier:sbt-coursier:1.1.0-M9",
          "com.typesafe.sbt:sbt-native-packager:1.3.3"
        )
      )
    )
    val args = RemainingArgs(Nil, Nil)

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    Resolve.printTask(params, pool, new PrintStream(stdout, true, "UTF-8"), System.err, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    val expectedOutput =
      """aopalliance:aopalliance:1.0:default
        |ch.qos.logback:logback-classic:1.2.1:default
        |ch.qos.logback:logback-core:1.2.1:default
        |com.eed3si9n:gigahorse-core_2.12:0.3.0:default
        |com.eed3si9n:gigahorse-okhttp_2.12:0.3.0:default
        |com.eed3si9n:shaded-scalajson_2.12:1.0.0-M4:default
        |com.eed3si9n:sjson-new-core_2.12:0.8.0:default
        |com.eed3si9n:sjson-new-murmurhash_2.12:0.8.0:default
        |com.eed3si9n:sjson-new-scalajson_2.12:0.8.0:default
        |com.fasterxml.jackson.core:jackson-annotations:2.8.0:default
        |com.fasterxml.jackson.core:jackson-core:2.8.8:default
        |com.fasterxml.jackson.core:jackson-databind:2.8.8:default
        |com.fasterxml.jackson.datatype:jackson-datatype-guava:2.8.8:default
        |com.fasterxml.jackson.jaxrs:jackson-jaxrs-base:2.8.8:default
        |com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.8.8:default
        |com.fasterxml.jackson.module:jackson-module-jaxb-annotations:2.8.8:default
        |com.github.jnr:jffi:1.2.9:default
        |com.github.jnr:jnr-constants:0.8.7:default
        |com.github.jnr:jnr-enxio:0.9:default
        |com.github.jnr:jnr-ffi:2.0.3:default
        |com.github.jnr:jnr-posix:3.0.12:default
        |com.github.jnr:jnr-unixsocket:0.8:default
        |com.github.jnr:jnr-x86asm:1.0.2:default
        |com.google.guava:guava:20.0:default
        |com.jcraft:jsch:0.1.46:default
        |com.lmax:disruptor:3.3.6:default
        |com.spotify:docker-client:8.9.0:default
        |com.squareup.okhttp3:okhttp:3.7.0:default
        |com.squareup.okhttp3:okhttp-urlconnection:3.7.0:default
        |com.squareup.okio:okio:1.12.0:default
        |com.typesafe:config:1.2.0:default
        |com.typesafe:ssl-config-core_2.12:0.2.2:default
        |com.typesafe.sbt:sbt-native-packager;sbtVersion=1.0;scalaVersion=2.12:1.3.3:compile
        |commons-codec:commons-codec:1.9:default
        |commons-io:commons-io:2.5:default
        |commons-lang:commons-lang:2.6:default
        |commons-logging:commons-logging:1.2:default
        |io.get-coursier:coursier-cache_2.12:1.1.0-M9:default
        |io.get-coursier:coursier-core_2.12:1.1.0-M9:default
        |io.get-coursier:coursier-extra_2.12:1.1.0-M9:default
        |io.get-coursier:coursier-scalaz-interop_2.12:1.1.0-M9:default
        |io.get-coursier:coursier_2.12:1.1.0-M9:default
        |io.get-coursier:lm-coursier_2.12:1.1.0-M9:default
        |io.get-coursier:sbt-coursier;sbtVersion=1.0;scalaVersion=2.12:1.1.0-M9:default
        |io.get-coursier:sbt-coursier-shared;sbtVersion=1.0;scalaVersion=2.12:1.1.0-M9:default
        |javax.annotation:javax.annotation-api:1.2:default
        |javax.annotation:jsr250-api:1.0:default
        |javax.enterprise:cdi-api:1.0:default
        |javax.inject:javax.inject:1:default
        |javax.ws.rs:javax.ws.rs-api:2.0.1:default
        |jline:jline:2.14.4:default
        |org.apache.ant:ant:1.10.1:default
        |org.apache.ant:ant-launcher:1.10.1:default
        |org.apache.commons:commons-compress:1.14:default
        |org.apache.httpcomponents:httpclient:4.5:default
        |org.apache.httpcomponents:httpcore:4.4.5:default
        |org.apache.logging.log4j:log4j-api:2.8.1:default
        |org.apache.logging.log4j:log4j-core:2.8.1:default
        |org.apache.maven:maven-aether-provider:3.2.2:default
        |org.apache.maven:maven-artifact:3.2.2:default
        |org.apache.maven:maven-core:3.2.2:default
        |org.apache.maven:maven-model:3.2.2:default
        |org.apache.maven:maven-model-builder:3.2.2:default
        |org.apache.maven:maven-plugin-api:3.2.2:default
        |org.apache.maven:maven-repository-metadata:3.2.2:default
        |org.apache.maven:maven-settings:3.2.2:default
        |org.apache.maven:maven-settings-builder:3.2.2:default
        |org.bouncycastle:bcpg-jdk15on:1.51:default
        |org.bouncycastle:bcpkix-jdk15on:1.52:default
        |org.bouncycastle:bcprov-jdk15on:1.52:default
        |org.codehaus.plexus:plexus-classworlds:2.5.1:default
        |org.codehaus.plexus:plexus-component-annotations:1.5.5:default
        |org.codehaus.plexus:plexus-interpolation:1.19:default
        |org.codehaus.plexus:plexus-utils:3.0.17:default
        |org.eclipse.aether:aether-api:0.9.0.M2:default
        |org.eclipse.aether:aether-impl:0.9.0.M2:default
        |org.eclipse.aether:aether-spi:0.9.0.M2:default
        |org.eclipse.aether:aether-util:0.9.0.M2:default
        |org.eclipse.sisu:org.eclipse.sisu.inject:0.0.0.M5:default
        |org.eclipse.sisu:org.eclipse.sisu.plexus:0.0.0.M5:default
        |org.glassfish.hk2:hk2-api:2.4.0-b34:default
        |org.glassfish.hk2:hk2-locator:2.4.0-b34:default
        |org.glassfish.hk2:hk2-utils:2.4.0-b34:default
        |org.glassfish.hk2:osgi-resource-locator:1.0.1:default
        |org.glassfish.hk2.external:aopalliance-repackaged:2.4.0-b34:default
        |org.glassfish.hk2.external:javax.inject:2.4.0-b34:default
        |org.glassfish.jersey.bundles.repackaged:jersey-guava:2.22.2:default
        |org.glassfish.jersey.connectors:jersey-apache-connector:2.22.2:default
        |org.glassfish.jersey.core:jersey-client:2.22.2:default
        |org.glassfish.jersey.core:jersey-common:2.22.2:default
        |org.glassfish.jersey.ext:jersey-entity-filtering:2.22.2:default
        |org.glassfish.jersey.media:jersey-media-json-jackson:2.22.2:default
        |org.javassist:javassist:3.18.1-GA:default
        |org.ow2.asm:asm:5.0.3:default
        |org.ow2.asm:asm-analysis:5.0.3:default
        |org.ow2.asm:asm-commons:5.0.3:default
        |org.ow2.asm:asm-tree:5.0.3:default
        |org.ow2.asm:asm-util:5.0.3:default
        |org.reactivestreams:reactive-streams:1.0.0:default
        |org.scala-lang:scala-compiler:2.12.3:default
        |org.scala-lang:scala-library:2.12.7:default
        |org.scala-lang:scala-reflect:2.12.3:default
        |org.scala-lang.modules:scala-parser-combinators_2.12:1.0.6:default
        |org.scala-lang.modules:scala-xml_2.12:1.1.0:default
        |org.scala-sbt:io_2.12:1.0.0:default
        |org.scala-sbt:launcher-interface:1.0.0:default
        |org.scala-sbt:librarymanagement-core_2.12:1.0.2:default
        |org.scala-sbt:librarymanagement-ivy_2.12:1.0.2:default
        |org.scala-sbt:util-cache_2.12:1.0.0:default
        |org.scala-sbt:util-interface:1.0.0:default
        |org.scala-sbt:util-logging_2.12:1.0.0:default
        |org.scala-sbt:util-position_2.12:1.0.0:default
        |org.scala-sbt.ivy:ivy:2.3.0-sbt-a3314352b638afbf0dca19f127e8263ed6f898bd:default
        |org.scalaz:scalaz-concurrent_2.12:7.2.24:default
        |org.scalaz:scalaz-core_2.12:7.2.24:default
        |org.scalaz:scalaz-effect_2.12:7.2.24:default
        |org.slf4j:slf4j-api:1.7.25:default
        |org.sonatype.plexus:plexus-cipher:1.4:default
        |org.sonatype.plexus:plexus-sec-dispatcher:1.3:default
        |org.sonatype.sisu:sisu-guice:3.1.0:default
        |org.spire-math:jawn-parser_2.12:0.10.4:default
        |org.vafer:jdeb:1.3:default
        |""".stripMargin

    assert(output === expectedOutput)
  }

  it should "resolve sbt 0.13 plugins" in {
    val options = SharedResolveOptions(
      dependencyOptions = DependencyOptions(
        sbtPlugin = List("org.scalameta:sbt-metals:0.8.0"),
        sbtVersion = "0.13"
      )
    )
    val args = RemainingArgs(Nil, Nil)

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    Resolve.printTask(params, pool, new PrintStream(stdout, true, "UTF-8"), System.err, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    val expectedOutput = "org.scalameta:sbt-metals;sbtVersion=0.13;scalaVersion=2.10:0.8.0:default\n"

    assert(output === expectedOutput)
  }

  it should "resolve the main artifact first in classpath order" in {
    val options = SharedResolveOptions(
      classpathOrder = Option(true)
    )
    val args = RemainingArgs(
      Seq("io.get-coursier:coursier-cli_2.12:1.1.0-M9"),
      Nil
    )

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    val ps = new PrintStream(stdout, true, "UTF-8")
    Resolve.printTask(params, pool, ps, ps, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    assert(output.startsWith("io.get-coursier:coursier-cli_2.12:1.1.0-M9"))
  }

  it should "print candidate artifact URLs" in {
    val options = ResolveOptions(
      candidateUrls = true
    )
    val args = RemainingArgs(Seq("com.github.alexarchambault:case-app_2.13:2.0.0-M9"), Nil)

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    val ps = new PrintStream(stdout, true, "UTF-8")
    Resolve.printTask(params, pool, ps, ps, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, StandardCharsets.UTF_8)
    val expectedOutput =
      """https://repo1.maven.org/maven2/com/github/alexarchambault/case-app_2.13/2.0.0-M9/case-app_2.13-2.0.0-M9.jar
        |https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.0/scala-library-2.13.0.jar
        |https://repo1.maven.org/maven2/com/github/alexarchambault/case-app-annotations_2.13/2.0.0-M9/case-app-annotations_2.13-2.0.0-M9.jar
        |https://repo1.maven.org/maven2/com/github/alexarchambault/case-app-util_2.13/2.0.0-M9/case-app-util_2.13-2.0.0-M9.jar
        |https://repo1.maven.org/maven2/com/chuusai/shapeless_2.13/2.3.3/shapeless_2.13-2.3.3.jar
        |""".stripMargin

    assert(output == expectedOutput)
  }

  it should "exclude root dependencies" in {
    val options = SharedResolveOptions(
      dependencyOptions = DependencyOptions(
        exclude = List("com.chuusai::shapeless")
      ),
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.13.2")
      )
    )
    val args = RemainingArgs(
      Seq(
        "com.github.alexarchambault::argonaut-shapeless_6.2:1.2.0-M12",
        "com.chuusai::shapeless:2.3.3"
      ),
      Nil
    )

    val stdout = new ByteArrayOutputStream

    val params = paramsOrThrow(options)

    val ps = new PrintStream(stdout, true, "UTF-8")
    Resolve.printTask(params, pool, ps, ps, args.all)
      .unsafeRun()(ec)

    val output = new String(stdout.toByteArray, "UTF-8")
    val expectedOutput =
      """com.github.alexarchambault:argonaut-shapeless_6.2_2.13:1.2.0-M12:default
        |io.argonaut:argonaut_2.13:6.2.4:default
        |org.scala-lang:scala-library:2.13.2:default
        |org.scala-lang:scala-reflect:2.13.2:default
        |""".stripMargin
    assert(output == expectedOutput)
  }

  def output(options: SharedResolveOptions, args: String*): String = {

    val stdout = new ByteArrayOutputStream
    val params = paramsOrThrow(options)

    val ps = new PrintStream(stdout, true, "UTF-8")
    Resolve.printTask(params, pool, ps, ps, args)
      .unsafeRun()(ec)

    new String(stdout.toByteArray, "UTF-8")
  }

  it should "ignore binary scala version" in {
    val options = SharedResolveOptions(
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.13")
      )
    )
    val output0 = output(
      options,
      "org.scala-lang:scala-library:2.12.11"
    )
    val expectedOutput =
      """org.scala-lang:scala-library:2.12.11:default
        |""".stripMargin
    assert(output0 == expectedOutput)
  }

  it should "ignore full scala version" in {
    val options = SharedResolveOptions(
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.13.2")
      )
    )
    val output0 = output(
      options,
      "org.scala-lang:scala-library:2.12.11"
    )
    val expectedOutput =
      """org.scala-lang:scala-library:2.12.11:default
        |""".stripMargin
    assert(output0 == expectedOutput)
  }

  it should "use binary scala version" in {
    val options = SharedResolveOptions(
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.11")
      )
    )
    val output0 = output(
      options,
      "com.chuusai::shapeless:2.3.3"
    )
    val expectedOutput =
      """com.chuusai:shapeless_2.11:2.3.3:default
        |org.scala-lang:scala-library:2.11.12:default
        |org.typelevel:macro-compat_2.11:1.1.1:default
        |""".stripMargin
    assert(output0 == expectedOutput)
  }

  it should "use full scala version" in {
    val options = SharedResolveOptions(
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.13.2")
      )
    )
    val output0 = output(
      options,
      "com.chuusai::shapeless:2.3.3"
    )
    val expectedOutput =
      """com.chuusai:shapeless_2.13:2.3.3:default
        |org.scala-lang:scala-library:2.13.2:default
        |""".stripMargin
    assert(output0 == expectedOutput)
  }

  it should "use lower full scala version" in {
    val options = SharedResolveOptions(
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.13.1")
      )
    )
    val output0 = output(
      options,
      "com.chuusai::shapeless:2.3.3"
    )
    val expectedOutput =
      """com.chuusai:shapeless_2.13:2.3.3:default
        |org.scala-lang:scala-library:2.13.1:default
        |""".stripMargin
    assert(output0 == expectedOutput)
  }

  it should "use full scala version and not list those available" in {
    val options = SharedResolveOptions(
      resolutionOptions = ResolutionOptions(
        scalaVersion = Some("2.13.1")
      )
    )
    val success =
      try {
        output(
          options,
          // non existing module
          // resolution should try to resolve 'com.chuusaiz:shapeless_2.13:2.3.3' nonetheless
          "com.chuusaiz::shapeless:2.3.3"
        )
        true
      } catch {
        case e: ResolveException =>
          val expectedMessage =
            """Resolution error: Error downloading com.chuusaiz:shapeless_2.13:2.3.3
              |  not found: HOME/.ivy2/local/com.chuusaiz/shapeless_2.13/2.3.3/ivys/ivy.xml
              |  not found: https://repo1.maven.org/maven2/com/chuusaiz/shapeless_2.13/2.3.3/shapeless_2.13-2.3.3.pom""".stripMargin
          val message = e.message.replace(System.getProperty("user.home"), "HOME")
          assert(message == expectedMessage)
          false
      }
    assert(!success, "Expected a resolution exception")
  }
}
