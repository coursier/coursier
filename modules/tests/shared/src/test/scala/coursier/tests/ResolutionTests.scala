package coursier.tests

import coursier.core.{
  Activation,
  ArtifactSource,
  Configuration,
  Dependency,
  MinimizedExclusions,
  Module,
  Repository,
  Resolution,
  ResolutionProcess,
  Variant,
  VariantSelector
}
import coursier.maven.MavenRepository
import coursier.tests.TestUtil._
import coursier.tests.compatibility._
import coursier.util.StringInterpolators._
import coursier.version.VersionConstraint
import utest._

import scala.async.Async.{async, await}

object ResolutionTests extends TestSuite {

  def resolve0(
    deps: Seq[Dependency],
    filter: Option[Dependency => Boolean] = None,
    forceVersions: Map[Module, VersionConstraint] = Map.empty,
    forceProperties: Map[String, String] = Map.empty
  ) = {
    val res = Resolution()
      .withRootDependencies(deps)
      .withFilter(filter)
      .withForceVersions0(forceVersions)
      .withForceProperties(forceProperties)
      .withOsInfo(Activation.Os.empty)
    ResolutionProcess(res)
      .run0(Platform.fetch(repositories))
      .future()
  }

  implicit class ProjectOps(val p: Project) extends AnyVal {
    def kv: ((Module, VersionConstraint), (ArtifactSource, Project)) =
      ((p.module, VersionConstraint.fromVersion(p.version0)), (testRepository, p))
  }

  val projects = Seq(
    Project(mod"acme:config", "1.3.0"),
    Project(
      mod"acme:play",
      "2.4.0",
      Seq(
        Variant.emptyConfiguration -> dep"acme:play-json:2.4.0"
      )
    ),
    Project(mod"acme:play-json", "2.4.0"),
    Project(
      mod"acme:play",
      "2.4.1",
      dependencies = Seq(
        Variant.emptyConfiguration -> dep"acme:play-json:$${play_json_version}",
        Variant.emptyConfiguration -> dep"$${project.groupId}:$${WithSpecialChar©}:1.3.0"
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
        Variant.emptyConfiguration -> dep"acme:play:2.4.1"
          .withMinimizedExclusions(MinimizedExclusions(Set((org"acme", name"config"))))
      )
    ),
    Project(
      mod"acme:play-extra-no-config-no",
      "2.4.1",
      Seq(
        Variant.emptyConfiguration -> dep"acme:play:2.4.1"
          .withMinimizedExclusions(MinimizedExclusions(Set((org"*", name"config"))))
      )
    ),
    Project(
      mod"acme:module-with-missing-pom",
      "1.0.0",
      dependencyManagement = Seq(
        Variant.Configuration(Configuration.`import`) -> dep"acme:missing-pom:1.0.0"
      )
    ),
    Project(
      mod"hudsucker:mail",
      "10.0",
      Seq(
        Variant.Configuration(Configuration.test) ->
          dep"$${project.groupId}:test-util:$${project.version}"
      )
    ),
    Project(mod"hudsucker:test-util", "10.0"),
    Project(
      mod"se.ikea:parent",
      "18.0",
      dependencyManagement = Seq(
        Variant.Configuration(Configuration.empty) -> dep"acme:play:2.4.0"
          .withMinimizedExclusions(MinimizedExclusions(Set((org"acme", name"play-json"))))
      )
    ),
    Project(
      mod"se.ikea:billy",
      "18.0",
      dependencies = Seq(
        Variant.emptyConfiguration -> dep"acme:play:"
      ),
      parent0 = Some((mod"se.ikea:parent", "18.0"))
    ),
    Project(
      mod"org.gnome:parent",
      "7.0",
      Seq(
        Variant.emptyConfiguration -> dep"org.gnu:glib:13.4"
      )
    ),
    Project(
      mod"org.gnome:panel-legacy",
      "7.0",
      dependencies = Seq(
        Variant.emptyConfiguration -> dep"org.gnome:desktop:$${project.version}"
      ),
      parent0 = Some(mod"org.gnome:parent", "7.0")
    ),
    Project(
      mod"gov.nsa:secure-pgp",
      "10.0",
      Seq(
        Variant.emptyConfiguration -> dep"gov.nsa:crypto:536.89"
      )
    ),
    Project(
      mod"com.mailapp:mail-client",
      "2.1",
      dependencies = Seq(
        Variant.emptyConfiguration -> dep"gov.nsa:secure-pgp:10.0"
          .withMinimizedExclusions(MinimizedExclusions(Set((org"*", name"$${crypto.name}"))))
      ),
      properties = Seq("crypto.name" -> "crypto", "dummy" -> "2")
    ),
    Project(
      mod"com.thoughtworks.paranamer:paranamer-parent",
      "2.6",
      dependencies = Seq(
        Variant.emptyConfiguration -> dep"junit:junit:"
      ),
      dependencyManagement = Seq(
        Variant.Configuration(Configuration.test) -> dep"junit:junit:4.11"
      )
    ),
    Project(
      mod"com.thoughtworks.paranamer:paranamer",
      "2.6",
      parent0 = Some(mod"com.thoughtworks.paranamer:paranamer-parent", "2.6")
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.3.3",
      profiles = Seq(
        Profile(
          "default",
          activeByDefault = Some(true),
          dependencies = Seq(
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
          )
        )
      )
    ),
    Project(
      mod"com.github.dummy:libb",
      "0.4.2",
      dependencies = Seq(
        Variant.emptyConfiguration -> dep"org.scalaverification:scala-verification:1.12.4"
      ),
      profiles = Seq(
        Profile(
          "default",
          activeByDefault = Some(true),
          dependencies = Seq(
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6",
            Configuration.test  -> dep"org.scalaverification:scala-verification:1.12.4"
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
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
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
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
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
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
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
      parent0 = Some(mod"com.github.dummy:libb-parent", "0.5.6"),
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile(
          "default",
          activation = Profile.Activation(properties = Seq("special" -> Some("!false"))),
          dependencies = Seq(
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
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
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
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
            Configuration.empty -> dep"org.escalier:librairie-standard:2.11.6"
          )
        )
      )
    ),
    Project(mod"an-org:a-name", "1.0"),
    Project(mod"an-org:a-name", "1.2"),
    Project(
      mod"an-org:a-lib",
      "1.0",
      Seq(Variant.emptyConfiguration -> dep"an-org:a-name:1.0")
    ),
    Project(mod"an-org:a-lib", "1.1"),
    Project(
      mod"an-org:a-lib",
      "1.2",
      Seq(Variant.emptyConfiguration -> dep"an-org:a-name:1.2")
    ),
    Project(
      mod"an-org:another-lib",
      "1.0",
      Seq(Variant.emptyConfiguration -> dep"an-org:a-name:1.0")
    ),

    // Must bring transitively an-org:a-name, as an optional dependency
    Project(
      mod"an-org:an-app",
      "1.0",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:a-lib:1.0"
          .withMinimizedExclusions(MinimizedExclusions(Set((org"an-org", name"a-name")))),
        Variant.emptyConfiguration -> dep"an-org:another-lib:1.0".withOptional(true)
      )
    ),
    Project(
      mod"an-org:an-app",
      "1.1",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:a-lib:1.1"
      )
    ),
    Project(
      mod"an-org:an-app",
      "1.2",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:a-lib:1.2"
      )
    ),
    Project(mod"an-org:my-lib-1", "1.0.0+build.027", Seq()),
    Project(mod"an-org:my-lib-1", "1.1.0+build.018", Seq()),
    Project(mod"an-org:my-lib-1", "1.2.0", Seq()),
    Project(
      mod"an-org:my-lib-2",
      "1.0",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:my-lib-1:1.0.0+build.027"
      )
    ),
    Project(
      mod"an-org:my-lib-3",
      "1.0",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:my-lib-1:1.1.0+build.018"
      )
    ),
    Project(
      mod"an-org:my-lib-3",
      "1.1",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:my-lib-1:1.2.0"
      )
    ),
    Project(
      mod"an-org:my-app",
      "1.0",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:my-lib-2:1.0",
        Variant.emptyConfiguration -> dep"an-org:my-lib-3:1.0"
      )
    ),
    Project(
      mod"an-org:my-app",
      "1.1",
      Seq(
        Variant.emptyConfiguration -> dep"an-org:my-lib-2:1.0",
        Variant.emptyConfiguration -> dep"an-org:my-lib-3:1.1"
      )
    )
  )

  val projectsMap = projects
    .map { p =>
      (
        (p.module, VersionConstraint.fromVersion(p.version0)),
        p.withConfigurations(MavenRepository.defaultConfigurations)
      )
    }
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
        val dep = dep"acme:playy:2.4.0"
        val res = await(resolve0(
          Seq(dep)
        ))

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope))
          .withErrorCache(Map(dep.moduleVersionConstraint -> Seq("Not found")))

        assert(res == expected)
      }
    }
    test("missingPom") {
      async {
        val dep = dep"acme:module-with-missing-pom:1.0.0"
        val res = await(resolve0(
          Seq(dep)
        ))

        val directDependencyErrors =
          for {
            dep <- res.dependencies.toSeq
            err <- res.errorCache
              .get(dep.moduleVersionConstraint)
              .toSeq
          } yield (dep, err)

        val errors = res.errors0

        // Error originates from a dependency import, not directly from a dependency
        assert(directDependencyErrors.isEmpty)

        // metadataErrors have that
        assert(
          errors == Seq((mod"acme:missing-pom", VersionConstraint("1.0.0")) -> List("Not found"))
        )
      }
    }
    test("single") {
      async {
        val dep = dep"acme:config:1.3.0"
        val res = await(resolve0(
          Seq(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope))
          .withProjectCache0(Map(
            dep.moduleVersionConstraint -> (
              testRepository,
              projectsMap(dep.moduleVersionConstraint)
            )
          ))

        assert(res == expected)
      }
    }
    test("oneTransitiveDependency") {
      async {
        val dep   = dep"acme:play:2.4.0"
        val trDep = dep"acme:play-json:2.4.0"
        val res = await(resolve0(
          Seq(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope, trDep.withDefaultScope))
          .withProjectCache0(
            Map(
              projectsMap(dep.moduleVersionConstraint).kv,
              projectsMap(trDep.moduleVersionConstraint).kv
            )
          )

        assert(res == expected)
      }
    }
    test("twoTransitiveDependencyWithProps") {
      async {
        val dep = dep"acme:play:2.4.1"
        val trDeps = Seq(
          dep"acme:play-json:2.4.0",
          dep"acme:config:1.3.0"
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("exclude") {
      async {
        val dep = dep"acme:play-extra-no-config:2.4.1"
        val trDeps = Seq(
          dep"acme:play:2.4.1"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"acme", name"config")))),
          dep"acme:play-json:2.4.0"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"acme", name"config"))))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("excludeOrgWildcard") {
      async {
        val dep = dep"acme:play-extra-no-config-no:2.4.1"
        val trDeps = Seq(
          dep"acme:play:2.4.1"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"*", name"config")))),
          dep"acme:play-json:2.4.0"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"*", name"config"))))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("filter") {
      async {
        val dep = dep"hudsucker:mail:10.0"
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope))

        assert(res == expected)
      }
    }
    test("parentDepMgmt") {
      async {
        val dep = dep"se.ikea:billy:18.0"
        val trDeps = Seq(
          dep"acme:play:2.4.0"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"acme", name"play-json"))))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches.clearDependencyOverrides

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("parentDependencies") {
      async {
        val dep = dep"org.gnome:panel-legacy:7.0"
        val trDeps = Seq(
          dep"org.gnu:glib:13.4",
          dep"org.gnome:desktop:7.0"
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("propertiesInExclusions") {
      async {
        val dep = dep"com.mailapp:mail-client:2.1"
        val trDeps = Seq(
          dep"gov.nsa:secure-pgp:10.0"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"*", name"crypto"))))
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("depMgmtInParentDeps") {
      async {
        val dep = dep"com.thoughtworks.paranamer:paranamer:2.6"
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope))

        assert(res == expected)
      }
    }
    test("depsFromDefaultProfile") {
      async {
        val dep = dep"com.github.dummy:libb:0.3.3"
        val trDeps = Seq(
          dep"org.escalier:librairie-standard:2.11.6"
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
    test("depsFromPropertyActivatedProfile") {
      val f =
        for (version <- Seq("0.5.3", "0.5.4", "0.5.5", "0.5.6", "0.5.8")) yield async {
          val dep = Dependency(mod"com.github.dummy:libb", VersionConstraint(version))
          val trDeps = Seq(
            dep"org.escalier:librairie-standard:2.11.6"
          )
          val res = await(resolve0(
            Seq(dep)
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(Seq(dep))
            .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

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
        val dep = dep"com.github.dummy:libb:0.5.7"
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope))

        assert(res == expected)
      }
    }
    test("depsScopeOverrideFromProfile") {
      async {
        // Like com.google.inject:guice:3.0 with org.sonatype.sisu.inject:cglib
        val dep = dep"com.github.dummy:libb:0.4.2"
        val trDeps = Seq(
          dep"org.escalier:librairie-standard:2.11.6"
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }

    test("exclusionsAndOptionalShouldGoAlong") {
      async {
        val dep = dep"an-org:an-app:1.0"
        val trDeps = Seq(
          dep"an-org:a-lib:1.0"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"an-org", name"a-name")))),
          dep"an-org:another-lib:1.0".withOptional(true),
          dep"an-org:a-name:1.0".withOptional(true)
        )
        val res = await(resolve0(
          Seq(dep),
          filter = Some(_ => true)
        )).clearCaches.clearFilter

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }

    test("exclusionsOfDependenciesFromDifferentPathsShouldNotCollide") {
      async {
        val deps = Seq(
          dep"an-org:an-app:1.0",
          dep"an-org:a-lib:1.0".withOptional(true)
        )
        val trDeps = Seq(
          dep"an-org:a-lib:1.0"
            .withMinimizedExclusions(MinimizedExclusions(Set((org"an-org", name"a-name")))),
          dep"an-org:another-lib:1.0".withOptional(true),
          dep"an-org:a-name:1.0".withOptional(true)
        )
        val res = await(resolve0(
          deps,
          filter = Some(_ => true)
        )).clearCaches.clearFilter

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies((deps ++ trDeps).map(_.withDefaultScope).toSet)

        assert(res == expected)
      }
    }

    test("dependencyOverrides") {
      test {
        async {
          val deps = Seq(
            dep"an-org:a-name:1.1"
          )
          val depOverrides = Map(
            mod"an-org:a-name" -> VersionConstraint("1.0")
          )

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(deps)
            .withDependencies(
              Set(
                dep"an-org:a-name:1.0"
              ).map(_.withDefaultScope)
            )
            .withForceVersions0(depOverrides)

          assert(res == expected)
        }
      }

      test - async {
        val deps = Seq(
          dep"an-org:an-app:1.1"
        )
        val depOverrides = Map(
          mod"an-org:a-lib" -> VersionConstraint("1.0")
        )

        val res = await(resolve0(
          deps,
          forceVersions = depOverrides
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              dep"an-org:an-app:1.1",
              dep"an-org:a-lib:1.0",
              dep"an-org:a-name:1.0"
            ).map(_.withDefaultScope)
          )
          .withForceVersions0(depOverrides)

        assert(res == expected)
      }

      test - async {
        val deps = Seq(
          dep"an-org:an-app:1.1"
        )
        val depOverrides = Map(
          mod"*:a-lib" -> VersionConstraint("1.0")
        )

        val res = await(resolve0(
          deps,
          forceVersions = depOverrides
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              dep"an-org:an-app:1.1",
              dep"an-org:a-lib:1.0",
              dep"an-org:a-name:1.0"
            ).map(_.withDefaultScope)
          )
          .withForceVersions0(depOverrides)

        assert(res == expected)
      }

      test {
        async {
          val deps = Seq(
            dep"an-org:an-app:1.2"
          )
          val depOverrides = Map(
            mod"an-org:a-lib" -> VersionConstraint("1.1")
          )

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(deps)
            .withDependencies(
              Set(
                dep"an-org:an-app:1.2",
                dep"an-org:a-lib:1.1"
              ).map(_.withDefaultScope)
            )
            .withForceVersions0(depOverrides)

          assert(res == expected)
        }
      }
    }

    test("parts") {
      test("propertySubstitution") {
        val res =
          Resolution.withProperties0(
            Seq(Variant.emptyConfiguration -> dep"a-company:a-name:$${a.property}"),
            Map("a.property"               -> "a-version")
          )
        val expected =
          Seq(Variant.emptyConfiguration -> dep"a-company:a-name:a-version")

        assert(res == expected)
      }
    }

    test("forcedProperties") {
      async {
        val deps = Seq(
          dep"com.github.dummy:libb:0.5.4"
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
              dep"com.github.dummy:libb:0.5.4"
            ).map(_.withDefaultScope)
          )
          .withForceProperties(forceProperties)

        assert(res == expected)
      }
    }

    test("mergingTransitiveDeps") {
      test - async {
        val dep = dep"an-org:my-app:1.0"
        val trDeps = Seq(
          dep"an-org:my-lib-1:1.1.0+build.018",
          dep"an-org:my-lib-2:1.0",
          dep"an-org:my-lib-3:1.0"
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }

      test - async {
        val dep = dep"an-org:my-app:1.1"
        val trDeps = Seq(
          dep"an-org:my-lib-1:1.2.0",
          dep"an-org:my-lib-2:1.0",
          dep"an-org:my-lib-3:1.1"
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withDefaultScope) ++ trDeps.map(_.withDefaultScope))

        assert(res == expected)
      }
    }
  }

}
