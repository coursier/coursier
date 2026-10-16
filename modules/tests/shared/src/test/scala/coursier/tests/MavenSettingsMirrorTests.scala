package coursier.tests

import coursier.Repositories
import coursier.core.Authentication
import coursier.maven.{MavenRepository, MavenSettings}
import coursier.params.MavenSettingsMirror
import utest._

object MavenSettingsMirrorTests extends TestSuite {

  private val central   = MavenRepository("https://repo1.maven.org/maven2")
  private val internal  = MavenRepository("https://repo.example.com/releases")
  private val insecure  = MavenRepository("http://repo.example.com/releases")
  private val localHttp = MavenRepository("http://localhost:8080/repository")
  private val localFile = MavenRepository("file:///home/alex/.m2/repository")

  private val mirrorUrl = "https://nexus.example.com/repository/maven-public"

  private def mirroredRoot(mirrorOf: String, repo: MavenRepository): Option[String] =
    MavenSettingsMirror(mirrorOf, mirrorUrl)
      .matches(repo)
      .map {
        case m: MavenRepository => m.root
        case other              => sys.error(s"Expected a MavenRepository, got $other")
      }

  val tests = Tests {
    test("wildcard") {
      assert(mirroredRoot("*", central) == Some(mirrorUrl))
      assert(mirroredRoot("*", internal) == Some(mirrorUrl))
      assert(mirroredRoot("*", localFile) == Some(mirrorUrl))
    }

    test("central") {
      assert(mirroredRoot("central", central) == Some(mirrorUrl))
      assert(mirroredRoot("central", internal) == None)
      assert(mirroredRoot("central", MavenRepository("https://repo.maven.apache.org/maven2")) ==
        Some(mirrorUrl))
    }

    test("rootUrlAsId") {
      assert(mirroredRoot("https://repo.example.com/releases", internal) == Some(mirrorUrl))
      assert(mirroredRoot("https://repo.example.com/releases/", internal) == Some(mirrorUrl))
      assert(mirroredRoot("https://repo.example.com/releases", central) == None)
    }

    test("external") {
      assert(mirroredRoot("external:*", central) == Some(mirrorUrl))
      assert(mirroredRoot("external:*", insecure) == Some(mirrorUrl))
      assert(mirroredRoot("external:*", localHttp) == None)
      assert(mirroredRoot("external:*", localFile) == None)
    }

    test("externalHttp") {
      assert(mirroredRoot("external:http:*", insecure) == Some(mirrorUrl))
      assert(mirroredRoot("external:http:*", central) == None)
      assert(mirroredRoot("external:http:*", localHttp) == None)
      assert(mirroredRoot("external:http:*", localFile) == None)
    }

    test("negation") {
      assert(mirroredRoot("*,!https://repo.example.com/releases", internal) == None)
      assert(mirroredRoot("*,!https://repo.example.com/releases", central) == Some(mirrorUrl))
      assert(mirroredRoot("external:*,!central", central) == None)
      // an exact match stops the iteration, so the negation coming after it is not considered
      assert(mirroredRoot("central,!central", central) == Some(mirrorUrl))
    }

    test("commaSeparatedIds") {
      assert(mirroredRoot(
        "central, https://repo.example.com/releases",
        internal
      ) == Some(mirrorUrl))
      assert(mirroredRoot("central, https://repo.example.com/releases", insecure) == None)
    }

    test("ignoresIvyRepositories") {
      val mirrored = MavenSettingsMirror("*", mirrorUrl).matches(Repositories.sbtPlugin("releases"))
      assert(mirrored == None)
    }

    test("keepsRepositoryType") {
      val mirrored = MavenSettingsMirror("*", mirrorUrl + "/").matches(central)
      val repo     = mirrored match {
        case Some(m: MavenRepository) => m
        case other                    => sys.error(s"Expected a MavenRepository, got $other")
      }
      assert(repo.root == mirrorUrl)
      assert(repo.urlFor(Seq("org", "foo")) == mirrorUrl + "/org/foo")
    }

    test("fromSettings") {
      test("credentialsFromServer") {
        val settings = MavenSettings(
          Seq(MavenSettings.Mirror("internal", mirrorUrl, "*")),
          Seq(MavenSettings.Server("internal", Some("alex"), Some("1234")))
        )

        val expected = Seq(
          MavenSettingsMirror("*", mirrorUrl, Some(Authentication("alex", "1234")))
        )

        assert(MavenSettingsMirror.fromSettings(settings) == expected)
      }

      test("noServer") {
        val settings = MavenSettings(
          Seq(MavenSettings.Mirror("internal", mirrorUrl, "*")),
          Seq(MavenSettings.Server("other", Some("alex"), Some("1234")))
        )

        val expected = Seq(MavenSettingsMirror("*", mirrorUrl))

        assert(MavenSettingsMirror.fromSettings(settings) == expected)
      }

      test("ignoresBlockedMirrors") {
        val settings = MavenSettings(
          Seq(MavenSettings.Mirror("blocker", "http://0.0.0.0/", "external:http:*", "", true)),
          Nil
        )

        assert(MavenSettingsMirror.fromSettings(settings).isEmpty)
      }

      test("layouts") {
        def mirror(mirrorOfLayouts: String) =
          MavenSettings.Mirror("internal", mirrorUrl, "*", mirrorOfLayouts, false)
        def kept(mirrorOfLayouts: String) =
          MavenSettingsMirror
            .fromSettings(MavenSettings(Seq(mirror(mirrorOfLayouts)), Nil))
            .nonEmpty

        assert(kept(""))
        assert(kept("*"))
        assert(kept("default"))
        assert(kept("default,legacy"))
        assert(!kept("legacy"))
        assert(!kept("*,!default"))
      }
    }
  }
}
