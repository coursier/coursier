package coursier.tests

import coursier.core.{
  Activation,
  ArtifactSource,
  Configuration,
  Dependency,
  Module,
  Repository,
  Resolution,
  ResolutionProcess
}
import coursier.maven.MavenRepository
import coursier.util.StringInterpolators._
import utest._

import scala.async.Async.{async, await}
import coursier.tests.TestUtil._
import coursier.tests.compatibility._

object ResolutionTests extends TestSuite {

  def resolve0(
    deps: Seq[Dependency],
    filter: Option[Dependency => Boolean] = None,
    forceVersions: Map[Module, String] = Map.empty,
    forceProperties: Map[String, String] = Map.empty
  ) = {
    val res = Resolution()
      .withRootDependencies(deps)
      .withFilter(filter)
      .withForceVersions(forceVersions)
      .withForceProperties(forceProperties)
      .withOsInfo(Activation.Os.empty)
    ResolutionProcess(res)
      .run(Platform.fetch(repositories))
      .future()
  }

  implicit class ProjectOps(val p: Project) extends AnyVal {
    def kv: ((Module, String), (ArtifactSource, Project)) = p.moduleVersion -> (testRepository, p)
  }

  val projects = Seq(
    Project(mod"acme:config", "1.3.0"),
    Project(
      mod"acme:play",
      "2.4.0",
      Seq(
        Configuration.empty -> dep"acme:play-json:2.4.0"
      )
    ),
    Project(mod"acme:play-json", "2.4.0"),
    Project(
      mod"acme:play",
      "2.4.1",
      dependencies = Seq(
        Configuration.empty -> Dependency(
          mod"acme:play-json",
          "${play_json_version}"
        ),
        Configuration.empty -> Dependency(
          mod"$${project.groupId}:$${WithSpecialChar©}",
          "1.3.0"
        )
      ),
      properties = Seq(
        "play_json_version" -> "2.4.0",
        "WithSpecialChar©"  -> "config"
      )
    ),
    Project(
      mod"acme:play-extra-no-config",
      "2.4.1",
      Seq(
        Configuration.empty -> Dependency(mod"acme:play", "2.4.1")
          .withExclusions(Set((org"acme", name"config")))
      )
    ),
    Project(
      mod"acme:play-extra-no-config-no",
      "2.4.1",
      Seq(
        Configuration.empty -> Dependency(mod"acme:play", "2.4.1")
          .withExclusions(Set((org"*", name"config")))
      )
    ),
    Project(
      mod"acme:module-with-missing-pom",
      "1.0.0",
      dependencyManagement = Seq(
        Configuration.`import` -> Dependency(mod"acme:missing-pom", "1.0.0")
      )
    ),
    Project(
      mod"hudsucker:mail",
      "10.0",
      Seq(
        Configuration.test -> Dependency(
          mod"$${project.groupId}:test-util",
          "${project.version}"
        )
      )
    ),
    Project(mod"hudsucker:test-util", "10.0"),
    Project(
      mod"se.ikea:parent",
      "18.0",
      dependencyManagement = Seq(
        Configuration.empty -> Dependency(mod"acme:play", "2.4.0")
          .withExclusions(Set((org"acme", name"play-json")))
      )
    ),
    Project(
      mod"se.ikea:billy",
      "18.0",
      dependencies = Seq(
        Configuration.empty -> Dependency(mod"acme:play", "")
      ),
      parent = Some(mod"se.ikea:parent", "18.0")
    ),
    Project(
      mod"org.gnome:parent",
      "7.0",
      Seq(
        Configuration.empty -> Dependency(mod"org.gnu:glib", "13.4")
      )
    ),
    Project(
      mod"org.gnome:panel-legacy",
      "7.0",
      dependencies = Seq(
        Configuration.empty -> Dependency(
          mod"org.gnome:desktop",
          "${project.version}"
        )
      ),
      parent = Some(mod"org.gnome:parent", "7.0")
    ),
    Project(
      mod"gov.nsa:secure-pgp",
      "10.0",
      Seq(
        Configuration.empty -> Dependency(mod"gov.nsa:crypto", "536.89")
      )
    ),
    Project(
      mod"com.mailapp:mail-client",
      "2.1",
      dependencies = Seq(
        Configuration.empty -> Dependency(mod"gov.nsa:secure-pgp", "10.0")
          .withExclusions(Set((org"*", name"$${crypto.name}")))
      ),
      properties = Seq("crypto.name" -> "crypto", "dummy" -> "2")
    ),
    Project(
      mod"com.thoughtworks.paranamer:paranamer-parent",
      "2.6",
      dependencies = Seq(
        Configuration.empty -> Dependency(mod"junit:junit", "")
      ),
      dependencyManagement = Seq(
        Configuration.test -> Dependency(mod"junit:junit", "4.11")
      )
    ),
    Project(
      mod"com.thoughtworks.paranamer:paranamer",
      "2.6",
      parent = Some(mod"com.thoughtworks.paranamer:paranamer-parent", "2.6")
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.3.3",
      profiles = Seq(
        Profile(
          "default",
          activeByDefault = Some(true),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.4.2",
      dependencies = Seq(
        Configuration.empty -> Dependency(
          mod"org.scalaverification:scala-verification",
          "1.12.4"
        )
      ),
      profiles = Seq(
        Profile(
          "default",
          activeByDefault = Some(true),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            ),
            Configuration.test -> Dependency(
              mod"org.scalaverification:scala-verification",
              "1.12.4"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.5.3",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("special" -> None)),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.5.4",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("special" -> Some("true"))),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.5.5",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("special" -> Some("!false"))),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb-parent",
      "0.5.6",
      properties = Seq("special" -> "true")
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.5.6",
      parent = Some(mod"com.github.dummy:libb-parent", "0.5.6"),
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("special" -> Some("!false"))),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.5.7",
      // This project demonstrates a build profile that activates only when
      // the property "special" is unset. Because "special" is set to "true"
      // here, the build profile should not be active and "librairie-standard"
      // should not be provided as a transitive dependency when resolved.
      //
      // We additionally include the property "!special" -> "true" to
      // disambiguate the absence of the "special" property versus
      // the presence of the "!special" property (which is probably not valid pom
      // anyways)
      properties = Seq("special" -> "true", "!special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("!special" -> None)),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.5.8",
      // This project demonstrates a build profile that activates only when
      // the property "special" is unset. Because that is the case here,
      // the "default" build profile should be active and "librairie-standard"
      // should be provided as a transitive dependency when resolved.
      //
      // We additionally include the property "!special" -> "true" to
      // disambiguate the absence of the "special" property versus
      // the presence of the "!special" property (which is probably not valid pom
      // anyways)
      properties = Seq("!special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("!special" -> None)),
          dependencies = Seq(
            Configuration.empty -> Dependency(
              mod"org.escalier:librairie-standard",
              "2.11.6"
            )
          )
        )
      )
    ),
    Project(mod"an-org:a-name", "1.0"),
    Project(mod"an-org:a-name", "1.2"),
    Project(
      mod"an-org:a-lib",
      "1.0",
      Seq(Configuration.empty -> Dependency(mod"an-org:a-name", "1.0"))
    ),
    Project(mod"an-org:a-lib", "1.1"),
    Project(
      mod"an-org:a-lib",
      "1.2",
      Seq(Configuration.empty -> Dependency(mod"an-org:a-name", "1.2"))
    ),
    Project(
      mod"an-org:another-lib",
      "1.0",
      Seq(Configuration.empty -> Dependency(mod"an-org:a-name", "1.0"))
    ),

    // Must bring transitively an-org:a-name, as an optional dependency
    Project(
      mod"an-org:an-app",
      "1.0",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:a-lib", "1.0").withExclusions(
          Set((org"an-org", name"a-name"))
        ),
        Configuration.empty -> Dependency(
          mod"an-org:another-lib",
          "1.0"
        ).withOptional(true)
      )
    ),
    Project(
      mod"an-org:an-app",
      "1.1",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:a-lib", "1.1")
      )
    ),
    Project(
      mod"an-org:an-app",
      "1.2",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:a-lib", "1.2")
      )
    ),
    Project(mod"an-org:my-lib-1", "1.0.0+build.027", Seq()),
    Project(mod"an-org:my-lib-1", "1.1.0+build.018", Seq()),
    Project(mod"an-org:my-lib-1", "1.2.0", Seq()),
    Project(
      mod"an-org:my-lib-2",
      "1.0",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:my-lib-1", "1.0.0+build.027")
      )
    ),
    Project(
      mod"an-org:my-lib-3",
      "1.0",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:my-lib-1", "1.1.0+build.018")
      )
    ),
    Project(
      mod"an-org:my-lib-3",
      "1.1",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:my-lib-1", "1.2.0")
      )
    ),
    Project(
      mod"an-org:my-app",
      "1.0",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:my-lib-2", "1.0"),
        Configuration.empty -> Dependency(mod"an-org:my-lib-3", "1.0")
      )
    ),
    Project(
      mod"an-org:my-app",
      "1.1",
      Seq(
        Configuration.empty -> Dependency(mod"an-org:my-lib-2", "1.0"),
        Configuration.empty -> Dependency(mod"an-org:my-lib-3", "1.1")
      )
    )
  )

  val projectsMap = projects
    .map(p => p.moduleVersion -> p.withConfigurations(MavenRepository.defaultConfigurations))
    .toMap
  val testRepository = TestRepository(projectsMap)

  val repositories = Seq[Repository](
    testRepository
  )

  val tests = Tests {
    test("empty") {
      async {
        val res = await(resolve0(
          Nil
        ))

        assert(res == Resolution())
      }
    }
    test("notFound") {
      async {
        val dep = Dependency(mod"acme:playy", "2.4.0")
        val res = await(resolve0(
          Seq(dep)
        ))

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))
          .withErrorCache(Map(dep.moduleVersion -> Seq("Not found")))

        assert(res == expected)
      }
    }
    test("missingPom") {
      async {
        val dep = Dependency(mod"acme:module-with-missing-pom", "1.0.0")
        val res = await(resolve0(
          Seq(dep)
        ))

        val directDependencyErrors =
          for {
            dep <- res.dependencies.toSeq
            err <- res.errorCache
              .get(dep.moduleVersion)
              .toSeq
          } yield (dep, err)

        // Error originates from a dependency import, not directly from a dependency
        assert(directDependencyErrors.isEmpty)

        // metadataErrors have that
        assert(
          res.errors == Seq((mod"acme:missing-pom", "1.0.0") -> List("Not found"))
        )
      }
    }
    test("single") {
      async {
        val dep = Dependency(mod"acme:config", "1.3.0")
        val res = await(resolve0(
          Seq(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))
          .withProjectCache(Map(
            dep.moduleVersion -> (testRepository, projectsMap(dep.moduleVersion))
          ))

        assert(res == expected)
      }
    }
    test("oneTransitiveDependency") {
      async {
        val dep   = Dependency(mod"acme:play", "2.4.0")
        val trDep = Dependency(mod"acme:play-json", "2.4.0")
        val res = await(resolve0(
          Seq(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope, trDep.withCompileScope))
          .withProjectCache(
            Map(
              projectsMap(dep.moduleVersion).kv,
              projectsMap(trDep.moduleVersion).kv
            )
          )

        assert(res == expected)
      }
    }
    test("twoTransitiveDependencyWithProps") {
      async {
        val dep = Dependency(mod"acme:play", "2.4.1")
        val trDeps = Seq(
          Dependency(mod"acme:play-json", "2.4.0"),
          Dependency(mod"acme:config", "1.3.0")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("exclude") {
      async {
        val dep = Dependency(mod"acme:play-extra-no-config", "2.4.1")
        val trDeps = Seq(
          Dependency(mod"acme:play", "2.4.1")
            .withExclusions(Set((org"acme", name"config"))),
          Dependency(mod"acme:play-json", "2.4.0")
            .withExclusions(Set((org"acme", name"config")))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("excludeOrgWildcard") {
      async {
        val dep = Dependency(mod"acme:play-extra-no-config-no", "2.4.1")
        val trDeps = Seq(
          Dependency(mod"acme:play", "2.4.1")
            .withExclusions(Set((org"*", name"config"))),
          Dependency(mod"acme:play-json", "2.4.0")
            .withExclusions(Set((org"*", name"config")))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("filter") {
      async {
        val dep = Dependency(mod"hudsucker:mail", "10.0")
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))

        assert(res == expected)
      }
    }
    test("parentDepMgmt") {
      async {
        val dep = Dependency(mod"se.ikea:billy", "18.0")
        val trDeps = Seq(
          Dependency(mod"acme:play", "2.4.0")
            .withExclusions(Set((org"acme", name"play-json")))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches.clearDependencyOverrides

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("parentDependencies") {
      async {
        val dep = Dependency(mod"org.gnome:panel-legacy", "7.0")
        val trDeps = Seq(
          Dependency(mod"org.gnu:glib", "13.4"),
          Dependency(mod"org.gnome:desktop", "7.0")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("propertiesInExclusions") {
      async {
        val dep = Dependency(mod"com.mailapp:mail-client", "2.1")
        val trDeps = Seq(
          Dependency(mod"gov.nsa:secure-pgp", "10.0").withExclusions(Set((
            org"*",
            name"crypto"
          )))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("depMgmtInParentDeps") {
      async {
        val dep = Dependency(mod"com.thoughtworks.paranamer:paranamer", "2.6")
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))

        assert(res == expected)
      }
    }
    test("depsFromDefaultProfile") {
      async {
        val dep = Dependency(mod"com.github.dummy:libb", "0.3.3")
        val trDeps = Seq(
          Dependency(mod"org.escalier:librairie-standard", "2.11.6")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    test("depsFromPropertyActivatedProfile") {
      val f =
        for (version <- Seq("0.5.3", "0.5.4", "0.5.5", "0.5.6", "0.5.8")) yield async {
          val dep = Dependency(mod"com.github.dummy:libb", version)
          val trDeps = Seq(
            Dependency(mod"org.escalier:librairie-standard", "2.11.6")
          )
          val res = await(resolve0(
            Seq(dep)
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(Seq(dep))
            .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

          assert(res == expected)
        }

      scala.concurrent.Future.sequence(f).map(_ => ())
    }
    test("depsFromProfileDisactivatedByPropertyAbsence") {
      // A build profile only activates in the absence of some property should
      // not be activated when that property is present.
      // ---
      // The target dependency in this test (com.github.dummy % libb % 0.5.7)
      // declares a profile that is only active when name=!special,
      // and names a transitive dependency (librairie-standard) that is only
      // active under that build profile. When we resolve a module with
      // the "special" attribute set to "true", the transitive dependency
      // should not appear.
      async {
        val dep = Dependency(mod"com.github.dummy:libb", "0.5.7")
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))

        assert(res == expected)
      }
    }
    test("depsScopeOverrideFromProfile") {
      async {
        // Like com.google.inject:guice:3.0 with org.sonatype.sisu.inject:cglib
        val dep = Dependency(mod"com.github.dummy:libb", "0.4.2")
        val trDeps = Seq(
          Dependency(mod"org.escalier:librairie-standard", "2.11.6")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }

    test("exclusionsAndOptionalShouldGoAlong") {
      async {
        val dep = Dependency(mod"an-org:an-app", "1.0")
        val trDeps = Seq(
          Dependency(mod"an-org:a-lib", "1.0").withExclusions(Set((
            org"an-org",
            name"a-name"
          ))),
          Dependency(mod"an-org:another-lib", "1.0").withOptional(true),
          Dependency(mod"an-org:a-name", "1.0").withOptional(true)
        )
        val res = await(resolve0(
          Seq(dep),
          filter = Some(_ => true)
        )).clearCaches.clearFilter

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }

    test("exclusionsOfDependenciesFromDifferentPathsShouldNotCollide") {
      async {
        val deps = Seq(
          Dependency(mod"an-org:an-app", "1.0"),
          Dependency(mod"an-org:a-lib", "1.0").withOptional(true)
        )
        val trDeps = Seq(
          Dependency(mod"an-org:a-lib", "1.0").withExclusions(Set((
            org"an-org",
            name"a-name"
          ))),
          Dependency(mod"an-org:another-lib", "1.0").withOptional(true),
          Dependency(mod"an-org:a-name", "1.0").withOptional(true)
        )
        val res = await(resolve0(
          deps,
          filter = Some(_ => true)
        )).clearCaches.clearFilter

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies((deps ++ trDeps).map(_.withCompileScope).toSet)

        assert(res == expected)
      }
    }

    test("dependencyOverrides") {
      test {
        async {
          val deps = Seq(
            Dependency(mod"an-org:a-name", "1.1")
          )
          val depOverrides = Map(
            mod"an-org:a-name" -> "1.0"
          )

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(deps)
            .withDependencies(
              Set(
                Dependency(mod"an-org:a-name", "1.0")
              ).map(_.withCompileScope)
            )
            .withForceVersions(depOverrides)

          assert(res == expected)
        }
      }

      test - async {
        val deps = Seq(
          Dependency(mod"an-org:an-app", "1.1")
        )
        val depOverrides = Map(
          mod"an-org:a-lib" -> "1.0"
        )

        val res = await(resolve0(
          deps,
          forceVersions = depOverrides
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              Dependency(mod"an-org:an-app", "1.1"),
              Dependency(mod"an-org:a-lib", "1.0"),
              Dependency(mod"an-org:a-name", "1.0")
            ).map(_.withCompileScope)
          )
          .withForceVersions(depOverrides)

        assert(res == expected)
      }

      test - async {
        val deps = Seq(
          Dependency(mod"an-org:an-app", "1.1")
        )
        val depOverrides = Map(
          mod"*:a-lib" -> "1.0"
        )

        val res = await(resolve0(
          deps,
          forceVersions = depOverrides
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              Dependency(mod"an-org:an-app", "1.1"),
              Dependency(mod"an-org:a-lib", "1.0"),
              Dependency(mod"an-org:a-name", "1.0")
            ).map(_.withCompileScope)
          )
          .withForceVersions(depOverrides)

        assert(res == expected)
      }

      test {
        async {
          val deps = Seq(
            Dependency(mod"an-org:an-app", "1.2")
          )
          val depOverrides = Map(
            mod"an-org:a-lib" -> "1.1"
          )

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(deps)
            .withDependencies(
              Set(
                Dependency(mod"an-org:an-app", "1.2"),
                Dependency(mod"an-org:a-lib", "1.1")
              ).map(_.withCompileScope)
            )
            .withForceVersions(depOverrides)

          assert(res == expected)
        }
      }
    }

    test("parts") {
      test("propertySubstitution") {
        val res =
          Resolution.withProperties(
            Seq(Configuration.empty -> Dependency(
              mod"a-company:a-name",
              "${a.property}"
            )),
            Map("a.property" -> "a-version")
          )
        val expected =
          Seq(Configuration.empty -> Dependency(mod"a-company:a-name", "a-version"))

        assert(res == expected)
      }
    }

    test("forcedProperties") {
      async {
        val deps = Seq(
          Dependency(mod"com.github.dummy:libb", "0.5.4")
        )

        val forceProperties = Map(
          "special" -> "false"
        )

        val res = await(
          resolve0(deps, forceProperties = forceProperties)
        ).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              Dependency(mod"com.github.dummy:libb", "0.5.4")
            ).map(_.withCompileScope)
          )
          .withForceProperties(forceProperties)

        assert(res == expected)
      }
    }

    test("mergingTransitiveDeps") {
      test - async {
        val dep = Dependency(mod"an-org:my-app", "1.0")
        val trDeps = Seq(
          Dependency(mod"an-org:my-lib-1", "1.1.0+build.018"),
          Dependency(mod"an-org:my-lib-2", "1.0"),
          Dependency(mod"an-org:my-lib-3", "1.0")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }

      test - async {
        val dep = Dependency(mod"an-org:my-app", "1.1")
        val trDeps = Seq(
          Dependency(mod"an-org:my-lib-1", "1.2.0"),
          Dependency(mod"an-org:my-lib-2", "1.0"),
          Dependency(mod"an-org:my-lib-3", "1.1")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
  }

}
