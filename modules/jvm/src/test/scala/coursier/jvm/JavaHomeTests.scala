package coursier.jvm

import java.io.File

import coursier.env.EnvironmentUpdate
import utest._

import scala.concurrent.ExecutionContext

object JavaHomeTests extends TestSuite {

  private val forbidCommands: JavaHome.CommandOutput =
    new JavaHome.CommandOutput {
      def run(command: Seq[String], keepErrorStream: Boolean, extraEnv: Seq[(String, String)]): Either[Int, String] =
        throw new Exception("should not run commands")
    }

  val tests = Tests {

    "environment update should be empty for system JVM" - {
      val edit = JavaHome.environmentFor(JavaHome.systemId, new File("/home/foo/jvm/openjdk-27"), isMacOs = false)
      assert(edit.isEmpty)
    }

    "environment update should update both JAVA_HOME and PATH on Linux or Windows" - {
      val expectedEdit = EnvironmentUpdate()
        .withSet(Seq("JAVA_HOME" -> "/home/foo/jvm/openjdk-27"))
        .withPathLikeAppends(Seq("PATH" -> "/home/foo/jvm/openjdk-27/bin"))
      val edit = JavaHome.environmentFor("openjdk@20", new File("/home/foo/jvm/openjdk-27"), isMacOs = false)
      assert(edit == expectedEdit)
    }

    "environment update should update only JAVA_HOME on macOS" - {
      val expectedEdit = EnvironmentUpdate()
        .withSet(Seq("JAVA_HOME" -> "/home/foo/jvm/openjdk-27"))
      val edit = JavaHome.environmentFor("openjdk@20", new File("/home/foo/jvm/openjdk-27"), isMacOs = true)
      assert(edit == expectedEdit)
    }


    "system JVM should respect JAVA_HOME" - {

      val env = Map("JAVA_HOME" -> "/home/foo/jvm/adopt-31")
      val home = JavaHome()
        .withGetEnv(Some(env.get))
        .withCommandOutput(forbidCommands)
        .withOs("linux")

      val expectedSystem = Some("/home/foo/jvm/adopt-31")
      val system = home.system().unsafeRun()(ExecutionContext.global).map(_.getAbsolutePath)
      assert(system == expectedSystem)
    }

    "system JVM should use /usr/libexec/java_home on macOS" - {

      val commandOutput: JavaHome.CommandOutput =
        new JavaHome.CommandOutput {
          def run(command: Seq[String], keepErrorStream: Boolean, extraEnv: Seq[(String, String)]): Either[Int, String] =
            if (command == Seq("/usr/libexec/java_home"))
              Right("/Library/JVMs/oracle-41")
            else
              throw new Exception(s"Unexpected command: $command")
        }

      val home = JavaHome()
        .withGetEnv(Some(_ => None))
        .withCommandOutput(commandOutput)
        .withOs("darwin")

      val expectedSystem = Some("/Library/JVMs/oracle-41")
      val system = home.system().unsafeRun()(ExecutionContext.global).map(_.getAbsolutePath)
      assert(system == expectedSystem)
    }

    "system JVM should use get Java home via -XshowSettings:properties on Linux and Windows" - {

      val commandOutput: JavaHome.CommandOutput =
        new JavaHome.CommandOutput {
          def run(command: Seq[String], keepErrorStream: Boolean, extraEnv: Seq[(String, String)]): Either[Int, String] =
            if (command == Seq("java", "-XshowSettings:properties", "-version")) {
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
            } else
              throw new Exception(s"Unexpected command: $command")
        }

      val home = JavaHome()
        .withGetEnv(Some(_ => None))
        .withCommandOutput(commandOutput)
        .withOs("linux")

      val expectedSystem = Some("/usr/lib/jvm/oracle-39b07")
      val system = home.system().unsafeRun()(ExecutionContext.global).map(_.getAbsolutePath)
      assert(system == expectedSystem)
    }

  }

}
