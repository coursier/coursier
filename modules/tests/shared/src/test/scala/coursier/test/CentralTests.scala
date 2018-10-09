package coursier
package test

import utest._

import scala.async.Async.{async, await}
import coursier.MavenRepository
import coursier.Platform.fetch
import coursier.core.{Classifier, Configuration, Extension, Type}
import coursier.test.compatibility._
import coursier.util.Task

import scala.concurrent.Future

object CentralTests extends CentralTests

abstract class CentralTests extends TestSuite {

  def centralBase = "https://repo1.maven.org/maven2"

  final def isActualCentral = centralBase == "https://repo1.maven.org/maven2"

  lazy val repositories = Seq[Repository](
    MavenRepository(centralBase)
  )

  private def fetch(repositories: Seq[Repository]): Fetch.Metadata[Task] =
    Fetch.from(repositories, compatibility.artifact)

  def resolve(
    deps: Set[Dependency],
    filter: Option[Dependency => Boolean] = None,
    extraRepos: Seq[Repository] = Nil,
    profiles: Option[Set[String]] = None
  ) = {
    val repositories0 = extraRepos ++ repositories

    val fetch0 = fetch(repositories0)

    Resolution(
      deps,
      filter = filter,
      userActivations = profiles.map(_.iterator.map(p => if (p.startsWith("!")) p.drop(1) -> false else p -> true).toMap)
    )
      .process
      .run(fetch0)
      .map { res =>

        val metadataErrors = res.errors
        val conflicts = res.conflicts
        val isDone = res.isDone
        assert(metadataErrors.isEmpty)
        assert(conflicts.isEmpty)
        assert(isDone)

        res
      }
      .future()
  }

  def resolutionCheck(
    module: Module,
    version: String,
    extraRepos: Seq[Repository] = Nil,
    configuration: Configuration = Configuration.empty,
    profiles: Option[Set[String]] = None
  ): Future[Unit] =
    async {
      val attrPathPart =
        if (module.attributes.isEmpty)
          ""
        else
          "/" + module.attributes.toVector.sorted.map {
            case (k, v) => k + "_" + v
          }.mkString("_")

      val path = Seq(
        "resolutions",
        module.organization.value,
        module.name.value,
        attrPathPart,
        version + (
          if (configuration.isEmpty)
            ""
          else
            "_" + configuration.value.replace('(', '_').replace(')', '_')
        )
      ).filter(_.nonEmpty).mkString("/")

      def tryRead = textResource(path)

      val dep = Dependency(module, version, configuration = configuration)
      val res = await(resolve(Set(dep), extraRepos = extraRepos, profiles = profiles))

      // making that lazy makes scalac crash in 2.10 with scalajs
      val result = res
        .minDependencies
        .toVector
        .map { dep =>
          val projOpt = res.projectCache
            .get(dep.moduleVersion)
            .map { case (_, proj) => proj }
          val dep0 = dep.copy(
            version = projOpt.fold(dep.version)(_.actualVersion)
          )
          (dep0.module.organization.value, dep0.module.nameWithAttributes, dep0.version, dep0.configuration.value)
        }
        .sorted
        .distinct
        .map {
          case (org, name, ver, cfg) =>
            Seq(org, name, ver, cfg).mkString(":")
        }

      val expected =
        await(
          tryRead.recoverWith {
            case _: Exception =>
              tryCreate(path, result.mkString("\n"))
              tryRead
          }
        ).split('\n').toSeq

      for (((e, r), idx) <- expected.zip(result).zipWithIndex if e != r)
        println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")

      assert(result == expected)
    }

  def withArtifacts[T](
    module: Module,
    version: String,
    attributes: Attributes = Attributes(),
    extraRepos: Seq[Repository] = Nil,
    classifierOpt: Option[Classifier] = None,
    transitive: Boolean = false
  )(
    f: Seq[Artifact] => T
  ): Future[T] = {
    val dep = Dependency(module, version, transitive = transitive, attributes = attributes)
    withArtifacts(dep, extraRepos, classifierOpt)(f)
  }

  def withArtifacts[T](
    dep: Dependency,
    extraRepos: Seq[Repository],
    classifierOpt: Option[Classifier]
  )(
    f: Seq[Artifact] => T
  ): Future[T] = 
    withArtifacts(Set(dep), extraRepos, classifierOpt)(f)

  def withArtifacts[T](
    deps: Set[Dependency],
    extraRepos: Seq[Repository],
    classifierOpt: Option[Classifier]
  )(
    f: Seq[Artifact] => T
  ): Future[T] =
    withDetailedArtifacts(deps, extraRepos, classifierOpt)(l => f(l.map(_._2)))

  def withDetailedArtifacts[T](
    deps: Set[Dependency],
    extraRepos: Seq[Repository],
    classifierOpt: Option[Classifier]
  )(
    f: Seq[(Attributes, Artifact)] => T
  ): Future[T] =
    async {
      val res = await(resolve(deps, extraRepos = extraRepos))

      val metadataErrors = res.errors
      val conflicts = res.conflicts
      val isDone = res.isDone
      assert(metadataErrors.isEmpty)
      assert(conflicts.isEmpty)
      assert(isDone)

      val artifacts = res.dependencyArtifacts(classifiers = classifierOpt.map(Seq(_))).map(t => (t._2, t._3))

      f(artifacts)
    }

  def ensureHasArtifactWithExtension(
    module: Module,
    version: String,
    extension: Extension,
    attributes: Attributes = Attributes(),
    extraRepos: Seq[Repository] = Nil
  ): Future[Unit] =
    withArtifacts(module, version, attributes = attributes, extraRepos = extraRepos) { artifacts =>
      assert(artifacts.exists(_.url.endsWith("." + extension.value)))
    }

  val tests = Tests {

    'logback - {
      async {
        val dep = Dependency(Module(org"ch.qos.logback", name"logback-classic"), "1.1.3")
        val res = await(resolve(Set(dep))).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module(org"ch.qos.logback", name"logback-core"), "1.1.3").withCompileScope,
            Dependency(Module(org"org.slf4j", name"slf4j-api"), "1.7.7").withCompileScope))

        assert(res == expected)
      }
    }

    'asm - {
      async {
        val dep = Dependency(Module(org"org.ow2.asm", name"asm-commons"), "5.0.2")
        val res = await(resolve(Set(dep))).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module(org"org.ow2.asm", name"asm-tree"), "5.0.2").withCompileScope,
            Dependency(Module(org"org.ow2.asm", name"asm"), "5.0.2").withCompileScope))

        assert(res == expected)
      }
    }

    'jodaVersionInterval - {
      async {
        val dep = Dependency(Module(org"joda-time", name"joda-time"), "[2.2,2.8]")
        val res0 = await(resolve(Set(dep)))
        val res = res0.clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope))

        assert(res == expected)
        assert(res0.projectCache.contains(dep.moduleVersion))

        val proj = res0.projectCache(dep.moduleVersion)._2
        assert(proj.version == "2.8")
      }
    }

    'spark - {
      * - resolutionCheck(
        Module(org"org.apache.spark", name"spark-core_2.11"),
        "1.3.1",
        profiles = Some(Set("hadoop-2.2"))
      )

      'scala210 - resolutionCheck(
        Module(org"org.apache.spark", name"spark-core_2.10"),
        "2.1.1",
        profiles = Some(Set("hadoop-2.6", "scala-2.10", "!scala-2.11"))
      )
    }

    'argonautShapeless - {
      resolutionCheck(
        Module(org"com.github.alexarchambault", name"argonaut-shapeless_6.1_2.11"),
        "0.2.0"
      )
    }

    'snapshotMetadata - {
      'simple - {
        val mod = Module(org"com.github.fommil", name"java-logging")
        val version = "1.2-SNAPSHOT"
        val extraRepo = MavenRepository("https://oss.sonatype.org/content/repositories/public/")

        * - resolutionCheck(
          mod,
          version,
          configuration = Configuration.runtime,
          extraRepos = Seq(extraRepo)
        )

        * - ensureHasArtifactWithExtension(
          mod,
          version,
          Extension.jar,
          Attributes(Type.jar),
          extraRepos = Seq(extraRepo)
        )
      }

      * - {
        val mod = Module(org"org.jitsi", name"jitsi-videobridge")
        val version = "1.0-SNAPSHOT"
        val extraRepos = Seq(
          MavenRepository("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases"),
          MavenRepository("https://github.com/jitsi/jitsi-maven-repository/raw/master/snapshots"),
          MavenRepository("https://jitpack.io")
        )

        * - resolutionCheck(
          mod,
          version,
          extraRepos = extraRepos
        )
      }
    }

    'versionProperty - {
      // nasty one - in its POM, its version contains "${parent.project.version}"
      resolutionCheck(
        Module(org"org.bytedeco.javacpp-presets", name"opencv"),
        "3.0.0-1.1"
      )
    }

    'parentProjectProperties - {
      resolutionCheck(
        Module(org"com.github.fommil.netlib", name"all"),
        "1.1.2"
      )
    }

    'projectProperties - {
      resolutionCheck(
        Module(org"org.glassfish.jersey.core", name"jersey-client"),
        "2.19"
      )
    }

    'parentDependencyManagementProperties - {
      resolutionCheck(
        Module(org"com.nativelibs4java", name"jnaerator-runtime"),
        "0.12"
      )
    }

    'propertySubstitution - {
      resolutionCheck(
        Module(org"org.drools", name"drools-compiler"),
        "7.0.0.Final"
      )
    }

    'artifactIdProperties - {
      resolutionCheck(
        Module(org"cc.factorie", name"factorie_2.11"),
        "1.2"
      )
    }

    'versionInterval - {
      if (isActualCentral)
        // that one involves version intervals, thus changing versions, so only
        // running it against our cached Central stuff
        resolutionCheck(
          Module(org"org.webjars.bower", name"malihu-custom-scrollbar-plugin"),
          "3.1.5"
        )
      else
        Future.successful(())
    }

    'latestRevision - {
      * - resolutionCheck(
        Module(org"com.chuusai", name"shapeless_2.11"),
        "[2.2.0,2.3-a1)"
      )

      * - resolutionCheck(
        Module(org"com.chuusai", name"shapeless_2.11"),
        "2.2.+"
      )

      * - resolutionCheck(
        Module(org"com.googlecode.libphonenumber", name"libphonenumber"),
        "[7.0,7.1)"
      )

      * - resolutionCheck(
        Module(org"com.googlecode.libphonenumber", name"libphonenumber"),
        "7.0.+"
      )
    }

    'versionFromDependency - {
      val mod = Module(org"org.apache.ws.commons", name"XmlSchema")
      val version = "1.1"
      val expectedArtifactUrl = s"$centralBase/org/apache/ws/commons/XmlSchema/1.1/XmlSchema-1.1.jar"

      * - resolutionCheck(mod, version)

      * - withArtifacts(mod, version, Attributes(Type.jar)) { artifacts =>
        assert(artifacts.exists(_.url == expectedArtifactUrl))
      }
    }

    'fixedVersionDependency - {
      val mod = Module(org"io.grpc", name"grpc-netty")
      val version = "0.14.1"

      resolutionCheck(mod, version)
    }

    'mavenScopes - {
      def check(config: Configuration) = resolutionCheck(
        Module(org"com.android.tools", name"sdklib"),
        "24.5.0",
        configuration = config
      )

      'compile - check(Configuration.compile)
      'runtime - check(Configuration.runtime)
    }

    'optionalScope - {

      def intransitiveCompiler(config: Configuration) =
        Dependency(
          Module(org"org.scala-lang", name"scala-compiler"), "2.11.8",
          configuration = config,
          transitive = false,
          attributes = Attributes(Type.jar)
        )

      withArtifacts(
        Set(
          intransitiveCompiler(Configuration.default),
          intransitiveCompiler(Configuration.optional)
        ),
        extraRepos = Nil,
        classifierOpt = None
      ) {
        case Seq() =>
          throw new Exception("Expected one JAR")
        case Seq(jar) =>
          () // ok
        case other =>
          throw new Exception(s"Got too many JARs (${other.mkString})")
      }
    }

    'packaging - {
      'aar - {
        // random aar-based module found on Central
        val module = Module(org"com.yandex.android", name"speechkit")
        val version = "2.5.0"

        * - ensureHasArtifactWithExtension(
          module,
          version,
          Extension("aar"),
          attributes = Attributes(Type("aar"))
        )

        * - ensureHasArtifactWithExtension(
          module,
          version,
          Extension("aar")
        )
      }

      'bundle - {
        // has packaging bundle - ensuring coursier gives its artifact the .jar extension
        * - ensureHasArtifactWithExtension(
          Module(org"com.google.guava", name"guava"),
          "17.0",
          Extension.jar
        )

        // even though packaging is bundle, depending on attribute type "jar" should still find
        // an artifact
        * - ensureHasArtifactWithExtension(
          Module(org"com.google.guava", name"guava"),
          "17.0",
          Extension.jar,
          attributes = Attributes(Type.jar)
        )
      }

      'mavenPlugin - {
        // has packaging maven-plugin - ensuring coursier gives its artifact the .jar extension
        ensureHasArtifactWithExtension(
          Module(org"org.bytedeco", name"javacpp"),
          "1.1",
          Extension.jar,
          Attributes(Type("maven-plugin"))
        )
      }
    }

    'classifier - {

      'vanilla - {
        async {
          val deps = Set(
            Dependency(
              Module(org"org.apache.avro", name"avro"), "1.8.1"
            )
          )
          val res = await(resolve(deps))
          val filenames: Set[String] = res.artifacts().map(_.url.split("/").last).toSet
          assert(filenames.contains("avro-1.8.1.jar"))
          assert(!filenames.contains("avro-1.8.1-tests.jar"))
        }
      }

      'tests - {
        async {
          val deps = Set(
            Dependency(
              Module(org"org.apache.avro", name"avro"), "1.8.1", attributes = Attributes(Type.empty, Classifier.tests)
            )
          )
          val res = await(resolve(deps))
          val filenames: Set[String] = res.artifacts().map(_.url.split("/").last).toSet
          assert(!filenames.contains("avro-1.8.1.jar"))
          assert(filenames.contains("avro-1.8.1-tests.jar"))
        }
      }

      'mixed - {
        async {
          val deps = Set(
            Dependency(
              Module(org"org.apache.avro", name"avro"), "1.8.1"
            ),
            Dependency(
              Module(org"org.apache.avro", name"avro"), "1.8.1", attributes = Attributes(Type.empty, Classifier.tests)
            )
          )
          val res = await(resolve(deps))
          val filenames: Set[String] = res.artifacts().map(_.url.split("/").last).toSet
          assert(filenames.contains("avro-1.8.1.jar"))
          assert(filenames.contains("avro-1.8.1-tests.jar"))
        }
      }
    }

    'artifacts - {
      'uniqueness - {
        async {
          val deps = Set(
            Dependency(
              Module(org"org.scala-lang", name"scala-compiler"), "2.11.8"
            ),
            Dependency(
              Module(org"org.scala-js", name"scalajs-compiler_2.11.8"), "0.6.8"
            )
          )

          val res = await(resolve(deps))

          val metadataErrors = res.errors
          val conflicts = res.conflicts
          val isDone = res.isDone
          assert(metadataErrors.isEmpty)
          assert(conflicts.isEmpty)
          assert(isDone)

          val artifacts = res.artifacts()

          val map = artifacts.groupBy(a => a)

          val nonUnique = map.filter {
            case (_, l) => l.length > 1
          }

          if (nonUnique.nonEmpty)
            println(
              "Found non unique artifacts:\n" +
                nonUnique.keys.toVector.map("  " + _).mkString("\n")
            )

          assert(nonUnique.isEmpty)
        }
      }

      'testJarType - {
        // dependencies with type "test-jar" should be given the classifier "tests" by default

        async {
          val deps = Set(
            Dependency(
              Module(org"org.apache.hadoop", name"hadoop-yarn-server-resourcemanager"),
              "2.7.1"
            )
          )

          val res = await(resolve(deps))

          val metadataErrors = res.errors
          val conflicts = res.conflicts
          val isDone = res.isDone
          assert(metadataErrors.isEmpty)
          assert(conflicts.isEmpty)
          assert(isDone)

          val dependencyArtifacts = res.dependencyArtifacts()

          val zookeeperTestArtifacts = dependencyArtifacts.collect {
            case (dep, attributes, artifact)
              if dep.module == Module(org"org.apache.zookeeper", name"zookeeper") &&
                 attributes.`type` == Type.testJar =>
              (attributes, artifact)
          }

          assert(zookeeperTestArtifacts.length == 1)

          val (attr, artifact) = zookeeperTestArtifacts.head

          assert(attr.`type` == Type.testJar)
          assert(attr.classifier == Classifier.tests)
          artifact.url.endsWith("-tests.jar")
        }
      }
    }

    'ignoreUtf8Bom - {
      resolutionCheck(
        Module(org"dk.brics.automaton", name"automaton"),
        "1.11-8"
      )
    }

    'ignoreWhitespaces - {
      resolutionCheck(
        Module(org"org.jboss.resteasy", name"resteasy-jaxrs"),
        "3.0.9.Final"
      )
    }

    'nd4jNative - {
      // In particular:
      // - uses OS-based activation,
      // - requires converting a "x86-64" to "x86_64" in it, and
      // - uses "project.packaging" property
      resolutionCheck(
        Module(org"org.nd4j", name"nd4j-native"),
        "0.5.0"
      )
    }

    'scalaCompilerJLine - {

      // optional should bring jline

      * - resolutionCheck(
        Module(org"org.scala-lang", name"scala-compiler"),
        "2.11.8"
      )

      * - resolutionCheck(
        Module(org"org.scala-lang", name"scala-compiler"),
        "2.11.8",
        configuration = Configuration.optional
      )
    }

    'deepLearning4j - {
      resolutionCheck(
        Module(org"org.deeplearning4j", name"deeplearning4j-core"),
        "0.8.0"
      )
    }

    'tarGzZipArtifacts - {
      val mod = Module(org"org.apache.maven", name"apache-maven")
      val version = "3.3.9"

      * - resolutionCheck(mod, version)

      val mainTarGzUrl = s"$centralBase/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.tar.gz"
      val mainZipUrl = s"$centralBase/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.zip"

      'tarGz - {
        * - {
          withArtifacts(mod, version, attributes = Attributes(Type("tar.gz"), Classifier("bin")), transitive = true) { artifacts =>
            assert(artifacts.nonEmpty)
            val urls = artifacts.map(_.url).toSet
            assert(urls.contains(mainTarGzUrl))
          }
        }
        * - {
          withArtifacts(mod, version, attributes = Attributes(Type("tar.gz"), Classifier("bin")), classifierOpt = Some(Classifier("bin")), transitive = true) { artifacts =>
            assert(artifacts.nonEmpty)
            val urls = artifacts.map(_.url).toSet
            assert(urls.contains(mainTarGzUrl))
          }
        }
      }

      'zip - {
        * - {
          withArtifacts(mod, version, attributes = Attributes(Type("zip"), Classifier("bin")), transitive = true) { artifacts =>
            assert(artifacts.nonEmpty)
            val urls = artifacts.map(_.url).toSet
            assert(urls.contains(mainZipUrl))
          }
        }
        * - {
          withArtifacts(mod, version, attributes = Attributes(Type("zip"), Classifier("bin")), classifierOpt = Some(Classifier("bin")), transitive = true) { artifacts =>
            assert(artifacts.nonEmpty)
            val urls = artifacts.map(_.url).toSet
            assert(urls.contains(mainZipUrl))
          }
        }
      }
    }

    'groupIdVersionProperties - {
      resolutionCheck(
        Module(org"org.apache.directory.shared", name"shared-ldap"),
        "0.9.19"
      )
    }

    'relocation - {
      * - resolutionCheck(
        Module(org"bouncycastle", name"bctsp-jdk14"),
        "138"
      )

      'ignoreRelocationJars - {
        val mod = Module(org"org.apache.commons", name"commons-io")
        val ver = "1.3.2"

        val expectedUrl = s"$centralBase/commons-io/commons-io/1.3.2/commons-io-1.3.2.jar"

        * - resolutionCheck(mod, ver)

        * - withArtifacts(mod, ver, transitive = true) { artifacts =>
          assert(artifacts.exists(_.url == expectedUrl))
        }
      }
    }

    'entities - {
      'odash - resolutionCheck(
        Module(org"org.codehaus.plexus", name"plexus"),
        "1.0.4"
      )
    }

    'parentVersionInPom - {
      resolutionCheck(
          Module(org"io.swagger.parser.v3", name"swagger-parser-v3"),
          "2.0.1"
        )
    }

    'parentBeforeImports - {
      resolutionCheck(
        Module(org"org.kie", name"kie-api"),
        "6.5.0.Final",
        extraRepos = Seq(MavenRepository("https://repository.jboss.org/nexus/content/repositories/public"))
      )
    }

    'signaturesOfSignatures - {
      val mod = Module(org"org.yaml", name"snakeyaml")
      val ver = "1.17"

      def hasSha1(a: Artifact) = a.checksumUrls.contains("SHA-1")
      def hasMd5(a: Artifact) = a.checksumUrls.contains("MD5")
      def hasSig(a: Artifact) = a.extra.contains("sig")

      * - resolutionCheck(mod, ver)

      * - withDetailedArtifacts(Set(Dependency(mod, ver, attributes = Attributes(Type.bundle))), Nil, None) { artifacts =>

        val jarOpt = artifacts.collect {
          case (attr, artifact) if attr.`type` == Type.bundle || attr.`type` == Type.jar =>
            artifact
        }

        assert(jarOpt.nonEmpty)
        assert(jarOpt.forall(hasSha1))
        assert(jarOpt.forall(hasMd5))
        assert(jarOpt.forall(hasSig))
      }

      * - withDetailedArtifacts(Set(Dependency(mod, ver, attributes = Attributes(Type.pom))), Nil, None) { artifacts =>

        val pomOpt = artifacts.collect {
          case (attr, artifact) if attr.`type` == Type.pom =>
            artifact
        }

        assert(pomOpt.nonEmpty)
        assert(pomOpt.forall(hasSha1))
        assert(pomOpt.forall(hasMd5))
        assert(pomOpt.forall(hasSig))
      }
    }

    'sbtPluginVersionRange - {
      val mod = Module(org"org.ensime", name"sbt-ensime", attributes = Map("scalaVersion" -> "2.10", "sbtVersion" -> "0.13"))
      val ver = "1.12.+"

      * - {
        if (isActualCentral) // doesn't work via proxies, which don't list all the upstream available versions
          resolutionCheck(mod, ver)
        else
          Future.successful(())
      }
    }

    'multiVersionRanges - {
      val mod = Module(org"org.webjars.bower", name"dgrid")
      val ver = "1.0.0"

      * - {
        if (isActualCentral) // if false, the tests rely on things straight from Central, which can be updated sometimes…
          resolutionCheck(mod, ver)
        else
          Future.successful(())
      }
    }

    'dependencyManagementScopeOverriding - {
      val mod = Module(org"org.apache.tika", name"tika-app")
      val ver = "1.13"

      * - resolutionCheck(mod, ver)
    }

    'optionalArtifacts - {
      val mod = Module(org"io.monix", name"monix_2.12")
      val ver = "2.3.0"

      val mainUrl = s"$centralBase/io/monix/monix_2.12/2.3.0/monix_2.12-2.3.0.jar"

      * - resolutionCheck(mod, ver)

      * - withArtifacts(mod, ver, Attributes(Type.jar)) { artifacts =>
        val mainArtifactOpt = artifacts.find(_.url == mainUrl)
        assert(mainArtifactOpt.nonEmpty)
        assert(mainArtifactOpt.forall(_.optional))
      }

      * - withArtifacts(Module(org"com.lihaoyi", name"scalatags_2.12"), "0.6.2", Attributes(Type.jar), transitive = true) { artifacts =>

        val urls = artifacts.map(_.url).toSet

        val expectedUrls = Seq(
          s"$centralBase/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar",
          s"$centralBase/com/lihaoyi/sourcecode_2.12/0.1.3/sourcecode_2.12-0.1.3.jar",
          s"$centralBase/com/lihaoyi/scalatags_2.12/0.6.2/scalatags_2.12-0.6.2.jar"
        )
        assert(expectedUrls.forall(urls))
      }
    }

    'packagingTpe - {
      val mod = Module(org"android.arch.lifecycle", name"extensions")
      val ver = "1.0.0-alpha3"

      val extraRepo = MavenRepository("https://maven.google.com")

      * - resolutionCheck(mod, ver, extraRepos = Seq(extraRepo))

      * - withArtifacts(mod, ver, Attributes(Type("aar")), extraRepos = Seq(extraRepo), transitive = true) { artifacts =>
        val urls = artifacts.map(_.url).toSet
        val expectedUrls = Set(
          "https://maven.google.com/com/android/support/support-fragment/25.3.1/support-fragment-25.3.1.aar",
          "https://maven.google.com/android/arch/core/core/1.0.0-alpha3/core-1.0.0-alpha3.aar",
          "https://maven.google.com/android/arch/lifecycle/runtime/1.0.0-alpha3/runtime-1.0.0-alpha3.aar",
          "https://maven.google.com/android/arch/lifecycle/extensions/1.0.0-alpha3/extensions-1.0.0-alpha3.aar",
          "https://maven.google.com/com/android/support/support-compat/25.3.1/support-compat-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-media-compat/25.3.1/support-media-compat-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-core-ui/25.3.1/support-core-ui-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-core-utils/25.3.1/support-core-utils-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-annotations/25.3.1/support-annotations-25.3.1.jar",
          "https://maven.google.com/android/arch/lifecycle/common/1.0.0-alpha3/common-1.0.0-alpha3.jar"
        )

        assert(expectedUrls.forall(urls))
      }
    }

    'noArtifactIdExclusion - {
      val mod = Module(org"org.datavec", name"datavec-api")
      val ver = "0.9.1"

      * - resolutionCheck(mod, ver)
    }

    'snapshotVersioningBundlePackaging - {
      val mod = Module(org"org.talend.daikon", name"daikon")
      val ver = "0.19.0-SNAPSHOT"

      val extraRepos = Seq(
        MavenRepository("https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceRelease"),
        MavenRepository("https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceSnapshot")
      )

      * - resolutionCheck(mod, ver, extraRepos = extraRepos)

      * - withArtifacts(mod, ver, Attributes(Type.jar), extraRepos = extraRepos, transitive = true) { artifacts =>
        val urls = artifacts.map(_.url).toSet
        val expectedUrls = Set(
          "https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceRelease/com/cedarsoftware/json-io/4.9.9-TALEND/json-io-4.9.9-TALEND.jar",
          "https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceSnapshot/org/talend/daikon/daikon/0.19.0-SNAPSHOT/daikon-0.19.0-20171201.100416-43.jar",
          s"$centralBase/com/fasterxml/jackson/core/jackson-annotations/2.5.3/jackson-annotations-2.5.3.jar",
          s"$centralBase/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar",
          s"$centralBase/com/fasterxml/jackson/core/jackson-databind/2.5.3/jackson-databind-2.5.3.jar",
          s"$centralBase/com/thoughtworks/paranamer/paranamer/2.7/paranamer-2.7.jar",
          s"$centralBase/commons-codec/commons-codec/1.6/commons-codec-1.6.jar",
          s"$centralBase/javax/inject/javax.inject/1/javax.inject-1.jar",
          s"$centralBase/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar",
          s"$centralBase/org/apache/avro/avro/1.8.1/avro-1.8.1.jar",
          s"$centralBase/org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar",
          s"$centralBase/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar",
          s"$centralBase/org/codehaus/jackson/jackson-core-asl/1.9.13/jackson-core-asl-1.9.13.jar",
          s"$centralBase/org/codehaus/jackson/jackson-mapper-asl/1.9.13/jackson-mapper-asl-1.9.13.jar",
          s"$centralBase/org/slf4j/slf4j-api/1.7.12/slf4j-api-1.7.12.jar",
          s"$centralBase/org/tukaani/xz/1.5/xz-1.5.jar",
          s"$centralBase/org/xerial/snappy/snappy-java/1.1.1.3/snappy-java-1.1.1.3.jar"
        )

        assert(expectedUrls.forall(urls))
      }
    }
  }

}
