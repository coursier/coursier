package coursier.cli.config

import java.nio.file.{Files, Paths}
import java.nio.file.attribute.PosixFilePermissions

import utest._

import scala.cli.config.{
  ConfigDb,
  Key,
  Keys,
  PasswordOption,
  PublishCredentials,
  RepositoryCredentials,
  Secret
}
import scala.util.Properties

object ConfigTests extends TestSuite {

  private val password = "1234-secret"

  private def withDb[T](f: ConfigDb => T): T = {
    val dir = Files.createTempDirectory("coursier-config-tests")
    try {
      val path = dir.resolve("config.json")
      val db   = ConfigDb.open(path).fold(e => throw new Exception(e), identity)
      f(db)
    }
    finally {
      Files.deleteIfExists(dir.resolve("config.json"))
      Files.deleteIfExists(dir)
    }
  }

  private def lines(db: ConfigDb, key: Key[?], showSecrets: Boolean) =
    Config.valueLines(db, key, showSecrets).toSeq.flatten.mkString

  val tests = Tests {
    test("repository credentials") {
      withDb { db =>
        db.set(
          Keys.repositoryCredentials,
          List(
            RepositoryCredentials(
              "repo.example.com",
              Some(PasswordOption.Value(Secret("alice"))),
              Some(PasswordOption.Value(Secret(password)))
            )
          )
        )
        val masked = lines(db, Keys.repositoryCredentials, showSecrets = false)
        assert(!masked.contains(password))
        assert(masked.contains("configRepo0.username=alice"))
        assert(masked.contains("configRepo0.password=****"))
        val shown = lines(db, Keys.repositoryCredentials, showSecrets = true)
        assert(shown.contains(s"configRepo0.password=$password"))
      }
    }

    test("password entry") {
      withDb { db =>
        db.set(Keys.proxyPassword, PasswordOption.Value(Secret(password)))
        val masked = lines(db, Keys.proxyPassword, showSecrets = false)
        assert(!masked.contains(password))
        assert(masked == "value:****")
        val shown = lines(db, Keys.proxyPassword, showSecrets = true)
        assert(shown == s"value:$password")

        db.set(Keys.proxyPassword, PasswordOption.Env("PROXY_PASSWORD"))
        val env = lines(db, Keys.proxyPassword, showSecrets = false)
        assert(env == "env:PROXY_PASSWORD")
      }
    }

    test("publish credentials") {
      withDb { db =>
        db.set(
          Keys.publishCredentials,
          List(
            PublishCredentials(
              "repo.example.com",
              Some(PasswordOption.Value(Secret("alice"))),
              Some(PasswordOption.Value(Secret(password)))
            )
          )
        )
        val masked = lines(db, Keys.publishCredentials, showSecrets = false)
        assert(!masked.contains(password))
        assert(masked.contains("alice:****@repo.example.com"))
        val shown = lines(db, Keys.publishCredentials, showSecrets = true)
        assert(shown.contains(s"alice:$password@repo.example.com"))
      }
    }

    test("wrong directory permissions") {
      if (Properties.isWin) "disabled"
      else {
        val dir = Files.createTempDirectory("coursier-config-tests")
        try {
          Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"))
          val path = dir.resolve("config.json")
          val db   = ConfigDb.open(path).fold(e => throw e, identity)
          db.set(Keys.repositoryMirrors, List("maven:*=https://repo.example.com/maven"))
          val error   = db.save(path).swap.getOrElse(sys.error("Expected an error"))
          val message = Config.configError(error, path).getMessage
          val expected = Seq(
            s"$dir has wrong permissions rwxr-xr-x (expected at most rwx------)",
            s"Restrict the directory permissions with 'chmod 700 $dir', or pass --config-file " +
              "with a file in another directory."
          )
          assert(message.linesIterator.toSeq == expected)
          assert(!Files.exists(path))
        }
        finally Files.deleteIfExists(dir)
      }
    }

    test("inline credentials") {
      withDb { db =>
        val path = Files.createTempDirectory("coursier-config-tests").resolve("config.json")
        val error = db
          .setFromString(Keys.repositoryCredentials, Seq("repo.example.com", "value:a", "value:b"))
          .swap
          .getOrElse(sys.error("Expected an error"))
        val message = Config.configError(error, path).getMessage
        assert(message.contains("Inline credentials not accepted"))
        assert(message.linesIterator.toSeq.last == s"Config file: $path")
        Files.delete(path.getParent)
      }
    }

    test("malformed config file") {
      val content = "{bad".getBytes("UTF-8")
      val error = ConfigDb(content, Some("config.json")).swap.getOrElse(
        sys.error("Expected an error")
      )
      val message = Config.configError(error, Paths.get("config.json")).getMessage
      // the first line of the JSON error, not its hex dump
      assert(message.linesIterator.length == 1)
      assert(message.startsWith("Error parsing config DB config.json: expected '\"'"))
    }

    test("help lists keys") {
      val help = ConfigOptions.helpMessage
      for (key <- Keys.all if !key.hidden)
        assert(help.contains(s"  ${key.fullName} "))
      for (key <- Keys.all if key.hidden)
        assert(!help.contains(s"  ${key.fullName} "))
      assert(help.contains("SCALA_CLI_CONFIG"))
      assert(help.contains("--config-file"))
    }
  }
}
