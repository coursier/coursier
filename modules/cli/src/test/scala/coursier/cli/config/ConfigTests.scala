package coursier.cli.config

import java.nio.file.Files

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
  }
}
