package coursier.jvm

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

import coursier.cache.{ArchiveCache, ArtifactError, Cache, MockCache}
import coursier.env.EnvironmentUpdate
import coursier.jvm.util.CommandOutput
import coursier.internal.InMemoryCache
import coursier.util.{Artifact, EitherT, Sync, Task}
import utest._

import scala.concurrent.ExecutionContext
import scala.util.Properties

object JavaHomeTests extends TestSuite {

  private val forbidCommands: CommandOutput =
    new CommandOutput {
      def run(
        command: Seq[String],
        keepErrorStream: Boolean,
        extraEnv: Seq[(String, String)]
      ): Either[Int, String] =
        throw new Exception("should not run commands")
    }

  private val poolInitialized = new AtomicBoolean(false)
  private lazy val pool = {
    val p = Sync.fixedThreadPool(6)
    poolInitialized.set(true)
    p
  }
  private implicit val ec = ExecutionContext.fromExecutorService(pool)

  override def utestAfterAll(): Unit =
    if (poolInitialized.getAndSet(false))
      pool.shutdown()

  private lazy val windowsDriveOpt =
    if (Properties.isWin) Some(new File(".").getAbsolutePath.takeWhile(_ != ':'))
    else None
  def platformPath(path: String) =
    windowsDriveOpt.fold(path)(dr => dr + ":" + path.replace('/', '\\'))

  val tests = Tests {

    test("environment update should be empty for system JVM") {
      val edit = JavaHome.environmentFor(
        true,
        new File("/home/foo/jvm/openjdk-27"),
        isMacOs = false
      )
      assert(edit.isEmpty)
    }

    test("environment update should update both JAVA_HOME and PATH on Linux or Windows") {
      val expectedEdit = EnvironmentUpdate()
        .withSet(Seq("JAVA_HOME" -> platformPath("/home/foo/jvm/openjdk-27")))
        .withPathLikeAppends(Seq("PATH" -> platformPath("/home/foo/jvm/openjdk-27/bin")))
      val edit =
        JavaHome.environmentFor(false, new File("/home/foo/jvm/openjdk-27"), isMacOs = false)
      assert(edit == expectedEdit)
    }

    test("environment update should update only JAVA_HOME on macOS") {
      val expectedEdit = EnvironmentUpdate()
        .withSet(Seq("JAVA_HOME" -> platformPath("/home/foo/jvm/openjdk-27")))
      val edit =
        JavaHome.environmentFor(false, new File("/home/foo/jvm/openjdk-27"), isMacOs = true)
      assert(edit == expectedEdit)
    }

    test("system JVM should respect JAVA_HOME") {

      val env = Map("JAVA_HOME" -> platformPath("/home/foo/jvm/adopt-31"))
      val home = JavaHome()
        .withGetEnv(Some(env.get))
        .withCommandOutput(forbidCommands)
        .withOs("linux")

      val expectedSystem = Some(platformPath("/home/foo/jvm/adopt-31"))
      val system         = home.system().unsafeRun()(ExecutionContext.global).map(_.getAbsolutePath)
      assert(system == expectedSystem)
    }

    test("system JVM should use /usr/libexec/java_home on macOS") {

      val commandOutput: CommandOutput =
        new CommandOutput {
          def run(
            command: Seq[String],
            keepErrorStream: Boolean,
            extraEnv: Seq[(String, String)]
          ): Either[Int, String] =
            if (command == Seq("/usr/libexec/java_home"))
              Right("/Library/JVMs/oracle-41")
            else
              throw new Exception(s"Unexpected command: $command")
        }

      val home = JavaHome()
        .withGetEnv(Some(_ => None))
        .withCommandOutput(commandOutput)
        .withOs("darwin")

      val expectedSystem = Some(platformPath("/Library/JVMs/oracle-41"))
      val system         = home.system().unsafeRun()(ExecutionContext.global).map(_.getAbsolutePath)
      assert(system == expectedSystem)
    }

    test("system JVM should use get Java home via -XshowSettings:properties on Linux and Windows") {

      val commandOutput: CommandOutput =
        new CommandOutput {
          def run(
            command: Seq[String],
            keepErrorStream: Boolean,
            extraEnv: Seq[(String, String)]
          ): Either[Int, String] =
            if (command == Seq("java", "-XshowSettings:properties", "-version"))
              if (keepErrorStream)
                Right(
                  """hello
                    |  a = b
                    |  b = b too
                    |  java.home = /usr/lib/jvm/oracle-39b07
                    |  user.home = /home/alex
                    |Oracle JDK 39b07
                    |""".stripMargin
                )
              else
                Right(
                  """Oracle JDK 39b07
                    |""".stripMargin
                )
            else
              throw new Exception(s"Unexpected command: $command")
        }

      val home = JavaHome()
        .withGetEnv(Some(_ => None))
        .withCommandOutput(commandOutput)
        .withOs("linux")

      val expectedSystem = Some(platformPath("/usr/lib/jvm/oracle-39b07"))
      val system         = home.system().unsafeRun()(ExecutionContext.global).map(_.getAbsolutePath)
      assert(system == expectedSystem)
    }

    test("prefer installed JVM over more recent one in index") {
      val strIndex =
        """{
          |  "the-os": {
          |    "the-arch": {
          |      "jdk@the-jdk": {
          |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1.tar.gz",
          |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2.tar.gz"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val index = JvmIndex.fromString(strIndex).fold(throw _, identity)

      JvmCacheTests.withTempDir { tmpDir =>
        val failCache: Cache[Task] =
          new Cache[Task] {
            val ec = ExecutionContext.fromExecutorService(pool)
            val fetch = _ =>
              EitherT[Task, String, String](Task.fail(new Exception("This cache must not be used")))
            def file(artifact: Artifact): EitherT[Task, ArtifactError, File] =
              EitherT[Task, ArtifactError, File](
                Task.fail(new Exception("This cache must not be used"))
              )
          }
        val failArchiveCache = ArchiveCache[Task](tmpDir.toFile).withCache(failCache)
        val csCache          = MockCache.create[Task](JvmCacheTests.mockDataLocation, pool)
        val archiveCache     = ArchiveCache[Task](tmpDir.toFile).withCache(csCache)
        val cache = JvmCache()
          .withArchiveCache(archiveCache)
          .withOs("the-os")
          .withArchitecture("the-arch")
          .withIndex(Task.point(index))
        val home = JavaHome()
          .withGetEnv(Some(_ => None))
          .withCommandOutput(forbidCommands)
          .withOs("the-os")
          .withCache(cache)
        val noUpdateHome = home
          .withNoUpdateCache(Some(cache))
          .withCache(cache.withArchiveCache(failArchiveCache))

        val initialCheckRes = home.getIfInstalled("the-jdk:1.1").unsafeRun()
        assert(initialCheckRes.isEmpty)

        val noUpdateInitialCheckRes = noUpdateHome.getIfInstalled("the-jdk:1.1").unsafeRun()
        assert(noUpdateInitialCheckRes.isEmpty)

        home.get("the-jdk:1.1").unsafeRun() // install 1.1

        val entries = index.lookup("the-jdk", "1", Some("the-os"), Some("the-arch"))
        assert(entries.exists(_.last.version == "1.2"))

        val ifInstalled = home.getWithIsSystemIfInstalled("the-jdk:1").unsafeRun()
        assert(ifInstalled.nonEmpty)
        assert(ifInstalled.map(_._1).contains(false))

        val (isSystem, _) = home.getWithIsSystem("the-jdk:1").unsafeRun()
        assert(!isSystem)
      }
    }
  }

}
