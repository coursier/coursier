package coursierapi

import coursier.cache.{CacheEnv, FileCache}
import coursier.credentials.{DirectCredentials, FileCredentials}
import coursier.internal.api.ApiHelper
import coursier.util.EnvValues
import utest._

import java.nio.file.Files
import java.util

object CacheTests extends TestSuite {

  val tests = Tests {

    test("credentials") {

      test("configFileCredentialsAutoLoaded") {
        val tmpFile = Files.createTempFile("test-credentials", ".properties")
        try {
          val content =
            """myrepo.host=artifacts.corp.com
              |myrepo.username=testuser
              |myrepo.password=testpass
              |""".stripMargin
          Files.write(tmpFile, content.getBytes)

          val credEnv = EnvValues(Some(tmpFile.toAbsolutePath.toUri.toString), None)
          val noEnv = EnvValues(None, None)
          val defaultCreds = CacheEnv.defaultCredentials(credEnv, noEnv, noEnv)

          val fc = FileCache().withCredentials(defaultCreds)
          val resolved = fc.credentials.flatMap {
            case dc: DirectCredentials => Seq(dc)
            case other => other.get()
          }

          assert(resolved.exists(_.host == "artifacts.corp.com"))
          assert(resolved.exists(dc => dc.usernameOpt.contains("testuser")))
          assert(resolved.exists(dc => dc.passwordOpt.exists(_.value == "testpass")))
        } finally {
          Files.deleteIfExists(tmpFile)
        }
      }

      test("defaultCredentialsNotOverridden") {
        val cache = Cache.create()
        val fc = ApiHelper.cache(cache)
        val defaultFc = FileCache()
        assert(fc.credentials == defaultFc.credentials)
      }

      test("explicitCredentialsAppendedToDefaults") {
        val cache = Cache.create()
          .addCredentials(Credentials.of("custom.host.com", "myuser", "mypass"))
        val fc = ApiHelper.cache(cache)
        val defaultFc = FileCache()
        assert(fc.credentials.size >= defaultFc.credentials.size + 1)
        val allDirect = fc.credentials.flatMap {
          case dc: DirectCredentials => Seq(dc)
          case other => other.get()
        }
        assert(allDirect.exists(_.host == "custom.host.com"))
      }

      test("multipleCredentialsFromPropertiesFile") {
        val tmpFile = Files.createTempFile("test-credentials-multi", ".properties")
        try {
          val content =
            """repo1.host=host1.example.com
              |repo1.username=user1
              |repo1.password=pass1
              |repo2.host=host2.example.com
              |repo2.username=user2
              |repo2.password=pass2
              |repo2.realm=MyRealm
              |repo2.https-only=true
              |""".stripMargin
          Files.write(tmpFile, content.getBytes)

          val credEnv = EnvValues(Some(tmpFile.toAbsolutePath.toUri.toString), None)
          val noEnv = EnvValues(None, None)
          val defaultCreds = CacheEnv.defaultCredentials(credEnv, noEnv, noEnv)

          val resolved = defaultCreds.flatMap {
            case dc: DirectCredentials => Seq(dc)
            case other => other.get()
          }

          assert(resolved.length == 2)
          val hosts = resolved.map(_.host).toSet
          assert(hosts == Set("host1.example.com", "host2.example.com"))

          val repo2 = resolved.find(_.host == "host2.example.com").get
          assert(repo2.realm.contains("MyRealm"))
          assert(repo2.httpsOnly)

          // Verify round-trip through Java API conversion
          val apiCred = ApiHelper.credentials(repo2)
          assert(apiCred.getHost == "host2.example.com")
          assert(apiCred.getRealm == "MyRealm")
          assert(apiCred.isHttpsOnly)

          val backToScala = ApiHelper.directCredentials(apiCred)
          assert(backToScala.host == "host2.example.com")
          assert(backToScala.realm.contains("MyRealm"))
          assert(backToScala.httpsOnly)
        } finally {
          Files.deleteIfExists(tmpFile)
        }
      }

      test("fetchAddCredentials") {
        val fetch = Fetch.create()
          .addCredentials(Credentials.of("fetch.host.com", "fuser", "fpass"))
        val creds = fetch.getCache.getCredentials
        assert(creds.size == 1)
        assert(creds.get(0).getHost == "fetch.host.com")
        assert(creds.get(0).getUser == "fuser")

        val fc = ApiHelper.cache(fetch.getCache)
        val allDirect = fc.credentials.flatMap {
          case dc: DirectCredentials => Seq(dc)
          case other => other.get()
        }
        assert(allDirect.exists(_.host == "fetch.host.com"))
      }

      test("completeAddCredentials") {
        val complete = Complete.create()
          .addCredentials(Credentials.of("complete.host.com", "cuser", "cpass"))
        val creds = complete.getCache.getCredentials
        assert(creds.size == 1)
        assert(creds.get(0).getHost == "complete.host.com")
      }

      test("versionsAddCredentials") {
        val versions = Versions.create()
          .addCredentials(Credentials.of("versions.host.com", "vuser", "vpass"))
        val creds = versions.getCache.getCredentials
        assert(creds.size == 1)
        assert(creds.get(0).getHost == "versions.host.com")
      }

      test("coursierCredentialsEnvInlineCredentials") {
        // Simulates COURSIER_CREDENTIALS env var with inline credentials
        val credEnv = EnvValues(Some("artifacts.corp.com user:password"), None)
        val noEnv = EnvValues(None, None)
        val creds = CacheEnv.defaultCredentials(credEnv, noEnv, noEnv)

        val resolved = creds.flatMap {
          case dc: DirectCredentials => Seq(dc)
          case other => other.get()
        }

        assert(resolved.exists(_.host == "artifacts.corp.com"))
        assert(resolved.exists(dc => dc.usernameOpt.contains("user")))
        assert(resolved.exists(dc => dc.passwordOpt.exists(_.value == "password")))
      }

      test("coursierCredentialsEnvFileCredentials") {
        // Simulates COURSIER_CREDENTIALS env var pointing to a properties file
        val tmpFile = Files.createTempFile("test-env-credentials", ".properties")
        try {
          val content =
            """myrepo.host=env-file.example.com
              |myrepo.username=envuser
              |myrepo.password=envpass
              |""".stripMargin
          Files.write(tmpFile, content.getBytes)

          val credEnv = EnvValues(Some(tmpFile.toAbsolutePath.toUri.toString), None)
          val noEnv = EnvValues(None, None)
          val creds = CacheEnv.defaultCredentials(credEnv, noEnv, noEnv)

          val resolved = creds.flatMap {
            case dc: DirectCredentials => Seq(dc)
            case other => other.get()
          }

          assert(resolved.exists(_.host == "env-file.example.com"))
          assert(resolved.exists(dc => dc.usernameOpt.contains("envuser")))
          assert(resolved.exists(dc => dc.passwordOpt.exists(_.value == "envpass")))
        } finally {
          Files.deleteIfExists(tmpFile)
        }
      }

      test("coursierCredentialsEnvPreservedThroughApiHelper") {
        // Proves that FileCache() defaults (which include COURSIER_CREDENTIALS)
        // are preserved when going through ApiHelper.cache(Cache.create())
        val cache = Cache.create()
        val fc = ApiHelper.cache(cache)
        val defaultFc = FileCache()

        // The credentials from FileCache defaults (which reads COURSIER_CREDENTIALS)
        // should be present in the FileCache created by ApiHelper
        assert(fc.credentials == defaultFc.credentials)

        // Adding explicit credentials should not displace the defaults
        val cacheWithExtra = Cache.create()
          .addCredentials(Credentials.of("extra.host.com", "eu", "ep"))
        val fcWithExtra = ApiHelper.cache(cacheWithExtra)
        assert(fcWithExtra.credentials.size == defaultFc.credentials.size + 1)
      }

      test("allPathsPreserveDefaults") {
        val defaultFc = FileCache()

        val fetchFc = ApiHelper.cache(Fetch.create().getCache)
        assert(fetchFc.credentials == defaultFc.credentials)

        val completeFc = ApiHelper.cache(Complete.create().getCache)
        assert(completeFc.credentials == defaultFc.credentials)

        val versionsFc = ApiHelper.cache(Versions.create().getCache)
        assert(versionsFc.credentials == defaultFc.credentials)

        val archiveFc = ApiHelper.cache(ArchiveCache.create().getCache)
        assert(archiveFc.credentials == defaultFc.credentials)
      }
    }

    test("credentialsApi") {

      test("factoryMethods") {
        val c1 = Credentials.of("user", "pass")
        assert(c1.getHost == "")
        assert(c1.getUser == "user")
        assert(c1.getPassword == "pass")
        assert(c1.getRealm == null)

        val c2 = Credentials.of("myhost.com", "user", "pass")
        assert(c2.getHost == "myhost.com")

        val c3 = Credentials.of("myhost.com", "user", "pass", "MyRealm")
        assert(c3.getRealm == "MyRealm")
      }

      test("builderMethods") {
        val cred = Credentials.of("user", "pass")
          .withHost("builder.host.com")
          .withRealm("TestRealm")
          .withOptional(false)
          .withMatchHost(false)
          .withHttpsOnly(true)
          .withPassOnRedirect(true)

        assert(cred.getHost == "builder.host.com")
        assert(cred.getRealm == "TestRealm")
        assert(!cred.isOptional)
        assert(!cred.isMatchHost)
        assert(cred.isHttpsOnly)
        assert(cred.isPassOnRedirect)
      }

      test("equalsAndHashCode") {
        val c1 = Credentials.of("host.com", "user", "pass", "realm")
          .withHttpsOnly(true)
        val c2 = Credentials.of("host.com", "user", "pass", "realm")
          .withHttpsOnly(true)
        val c3 = Credentials.of("other.com", "user", "pass", "realm")

        assert(c1 == c2)
        assert(c1.hashCode == c2.hashCode)
        assert(c1 != c3)
      }

      test("cacheWithCredentialsList") {
        val list = new util.ArrayList[Credentials]()
        list.add(Credentials.of("h1.com", "u1", "p1"))
        list.add(Credentials.of("h2.com", "u2", "p2"))

        val cache = Cache.create().withCredentials(list)
        assert(cache.getCredentials.size == 2)

        // withCredentials(varargs) overload
        val cache2 = Cache.create().withCredentials(
          Credentials.of("h3.com", "u3", "p3"),
          Credentials.of("h4.com", "u4", "p4")
        )
        assert(cache2.getCredentials.size == 2)
        assert(cache2.getCredentials.get(0).getHost == "h3.com")
        assert(cache2.getCredentials.get(1).getHost == "h4.com")
      }

      test("cacheAddCredentialsAccumulates") {
        val cache = Cache.create()
          .addCredentials(Credentials.of("h1.com", "u1", "p1"))
          .addCredentials(Credentials.of("h2.com", "u2", "p2"))
        assert(cache.getCredentials.size == 2)
        assert(cache.getCredentials.get(0).getHost == "h1.com")
        assert(cache.getCredentials.get(1).getHost == "h2.com")
      }

      test("credentialsImmutableFromGetter") {
        val cache = Cache.create()
          .addCredentials(Credentials.of("h1.com", "u1", "p1"))
        val thrown = try {
          cache.getCredentials.add(Credentials.of("h2.com", "u2", "p2"))
          false
        } catch {
          case _: UnsupportedOperationException => true
        }
        assert(thrown)
      }

      test("fileCredentialsPassedToFileCache") {
        val tmpFile = Files.createTempFile("test-file-creds", ".properties")
        try {
          val content =
            """myrepo.host=filecred.example.com
              |myrepo.username=fileuser
              |myrepo.password=filepass
              |""".stripMargin
          Files.write(tmpFile, content.getBytes)

          val cache = Cache.create()
            .addFileCredentials(tmpFile.toAbsolutePath.toString)
          val fc = ApiHelper.cache(cache)

          // FileCredentials should be present in the FileCache credentials
          val hasFileCred = fc.credentials.exists {
            case fc: FileCredentials => fc.path == tmpFile.toAbsolutePath.toString
            case _ => false
          }
          assert(hasFileCred)

          // Resolve and verify the credentials load correctly
          val resolved = fc.credentials.flatMap {
            case dc: DirectCredentials => Seq(dc)
            case other => other.get()
          }
          assert(resolved.exists(_.host == "filecred.example.com"))
          assert(resolved.exists(dc => dc.usernameOpt.contains("fileuser")))
        } finally {
          Files.deleteIfExists(tmpFile)
        }
      }

      test("fileCredentialsRoundTrip") {
        val cache = Cache.create()
          .addFileCredentials("/some/path/credentials.properties")
          .addFileCredentials("/other/path/creds.properties")

        // Java API -> Scala FileCache -> Java API
        val fc = ApiHelper.cache(cache)
        val cache2 = ApiHelper.cache(fc)

        // Round-trip includes default FileCredentials from CacheDefaults plus our explicit ones
        val files = cache2.getCredentialFiles
        assert(files.contains("/some/path/credentials.properties"))
        assert(files.contains("/other/path/creds.properties"))
      }

      test("fetchAddFileCredentials") {
        val fetch = Fetch.create()
          .addFileCredentials("/my/credentials.properties")
        assert(fetch.getCache.getCredentialFiles.size == 1)
        assert(fetch.getCache.getCredentialFiles.get(0) == "/my/credentials.properties")
      }

      test("perRepoCredentialsWithAllFields") {
        val cred = Credentials.of("admin", "secret")
          .withRealm("CorpRealm")
          .withHttpsOnly(true)
          .withPassOnRedirect(true)

        val repo = MavenRepository.of("https://repo.corp.com/releases")
          .withCredentials(cred)

        val repo0 = ApiHelper.repository(ApiHelper.repository(repo))
        assert(repo == repo0)

        // Verify all credential fields survive the round-trip
        val repoCred = repo0.asInstanceOf[MavenRepository].getCredentials
        assert(repoCred.getUser == "admin")
        assert(repoCred.getPassword == "secret")
        assert(repoCred.getRealm == "CorpRealm")
        assert(repoCred.isHttpsOnly)
        assert(repoCred.isPassOnRedirect)
      }
    }
  }
}
