package coursier
package test

import coursier.core.Repository
import coursier.maven.MavenRepository
import utest._
import scala.async.Async.{ async, await }

import coursier.test.compatibility._

object ResolutionTests extends TestSuite {

  def resolve0(
    deps: Set[Dependency],
    filter: Option[Dependency => Boolean] = None,
    forceVersions: Map[Module, String] = Map.empty,
    forceProperties: Map[String, String] = Map.empty
  ) =
    Resolution(deps, filter = filter, forceVersions = forceVersions, forceProperties = forceProperties)
      .process
      .run(Platform.fetch(repositories))
      .future()

  implicit class ProjectOps(val p: Project) extends AnyVal {
    def kv: (ModuleVersion, (Artifact.Source, Project)) = p.moduleVersion -> (testRepository, p)
  }

  val projects = Seq(
    Project(Module(org"acme", "config"), "1.3.0"),

    Project(Module(org"acme", "play"), "2.4.0", Seq(
      "" -> Dependency(Module(org"acme", "play-json"), "2.4.0"))),

    Project(Module(org"acme", "play-json"), "2.4.0"),

    Project(Module(org"acme", "play"), "2.4.1",
      dependencies = Seq(
        "" -> Dependency(Module(org"acme", "play-json"), "${play_json_version}"),
        "" -> Dependency(Module(org"$${project.groupId}", "${WithSpecialChar©}"), "1.3.0")),
      properties = Seq(
        "play_json_version" -> "2.4.0",
        "WithSpecialChar©" -> "config")),

    Project(Module(org"acme", "play-extra-no-config"), "2.4.1",
      Seq(
        "" -> Dependency(Module(org"acme", "play"), "2.4.1",
          exclusions = Set((org"acme", "config"))))),

    Project(Module(org"acme", "play-extra-no-config-no"), "2.4.1",
      Seq(
        "" -> Dependency(Module(org"acme", "play"), "2.4.1",
          exclusions = Set((org"*", "config"))))),

    Project(Module(org"acme", "module-with-missing-pom"), "1.0.0",
      dependencyManagement = Seq(
        "import" -> Dependency(Module(org"acme", "missing-pom"), "1.0.0"))),

    Project(Module(org"hudsucker", "mail"), "10.0",
      Seq(
        "test" -> Dependency(Module(org"$${project.groupId}", "test-util"), "${project.version}"))),

    Project(Module(org"hudsucker", "test-util"), "10.0"),

    Project(Module(org"se.ikea", "parent"), "18.0",
      dependencyManagement = Seq(
        "" -> Dependency(Module(org"acme", "play"), "2.4.0",
          exclusions = Set((org"acme", "play-json"))))),

    Project(Module(org"se.ikea", "billy"), "18.0",
      dependencies = Seq(
        "" -> Dependency(Module(org"acme", "play"), "")),
      parent = Some(Module(org"se.ikea", "parent"), "18.0")),

    Project(Module(org"org.gnome", "parent"), "7.0",
      Seq(
        "" -> Dependency(Module(org"org.gnu", "glib"), "13.4"))),

    Project(Module(org"org.gnome", "panel-legacy"), "7.0",
      dependencies = Seq(
        "" -> Dependency(Module(org"org.gnome", "desktop"), "${project.version}")),
      parent = Some(Module(org"org.gnome", "parent"), "7.0")),

    Project(Module(org"gov.nsa", "secure-pgp"), "10.0",
      Seq(
        "" -> Dependency(Module(org"gov.nsa", "crypto"), "536.89"))),

    Project(Module(org"com.mailapp", "mail-client"), "2.1",
      dependencies = Seq(
        "" -> Dependency(Module(org"gov.nsa", "secure-pgp"), "10.0",
          exclusions = Set((org"*", "${crypto.name}")))),
      properties = Seq("crypto.name" -> "crypto", "dummy" -> "2")),

    Project(Module(org"com.thoughtworks.paranamer", "paranamer-parent"), "2.6",
      dependencies = Seq(
        "" -> Dependency(Module(org"junit", "junit"), "")),
      dependencyManagement = Seq(
        "test" -> Dependency(Module(org"junit", "junit"), "4.11"))),

    Project(Module(org"com.thoughtworks.paranamer", "paranamer"), "2.6",
      parent = Some(Module(org"com.thoughtworks.paranamer", "paranamer-parent"), "2.6")),

    Project(Module(org"com.github.dummy", "libb"), "0.3.3",
      profiles = Seq(
        Profile("default", activeByDefault = Some(true), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", "libb"), "0.4.2",
      dependencies = Seq(
        "" -> Dependency(Module(org"org.scalaverification", "scala-verification"), "1.12.4")),
      profiles = Seq(
        Profile("default", activeByDefault = Some(true), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"),
          "test" -> Dependency(Module(org"org.scalaverification", "scala-verification"), "1.12.4"))))),

    Project(Module(org"com.github.dummy", "libb"), "0.5.3",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> None)), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", "libb"), "0.5.4",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("true"))), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", "libb"), "0.5.5",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("!false"))), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", "libb-parent"), "0.5.6",
      properties = Seq("special" -> "true")),

    Project(Module(org"com.github.dummy", "libb"), "0.5.6",
      parent = Some(Module(org"com.github.dummy", "libb-parent"), "0.5.6"),
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("!false"))), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", "libb"), "0.5.7",
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
        Profile("default", activation = Profile.Activation(properties = Seq("!special" -> None)), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", "libb"), "0.5.8",
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
        Profile("default", activation = Profile.Activation(properties = Seq("!special" -> None)), dependencies = Seq(
          "" -> Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))))),

    Project(Module(org"an-org", "a-name"), "1.0"),

    Project(Module(org"an-org", "a-name"), "1.2"),

    Project(Module(org"an-org", "a-lib"), "1.0",
      Seq("" -> Dependency(Module(org"an-org", "a-name"), "1.0"))),

    Project(Module(org"an-org", "a-lib"), "1.1"),

    Project(Module(org"an-org", "a-lib"), "1.2",
      Seq("" -> Dependency(Module(org"an-org", "a-name"), "1.2"))),

    Project(Module(org"an-org", "another-lib"), "1.0",
      Seq("" -> Dependency(Module(org"an-org", "a-name"), "1.0"))),

    // Must bring transitively an-org:a-name, as an optional dependency
    Project(Module(org"an-org", "an-app"), "1.0",
      Seq(
        "" -> Dependency(Module(org"an-org", "a-lib"), "1.0", exclusions = Set((org"an-org", "a-name"))),
        "" -> Dependency(Module(org"an-org", "another-lib"), "1.0", optional = true))),

    Project(Module(org"an-org", "an-app"), "1.1",
      Seq(
        "" -> Dependency(Module(org"an-org", "a-lib"), "1.1"))),

    Project(Module(org"an-org", "an-app"), "1.2",
      Seq(
        "" -> Dependency(Module(org"an-org", "a-lib"), "1.2")))
  )

  val projectsMap = projects.map(p => p.moduleVersion -> p.copy(configurations = MavenRepository.defaultConfigurations)).toMap
  val testRepository = TestRepository(projectsMap)

  val repositories = Seq[Repository](
    testRepository
  )

  val tests = Tests {
    'empty{
      async{
        val res = await(resolve0(
          Set.empty
        ))

        assert(res == Resolution.empty)
      }
    }
    'notFound{
      async {
        val dep = Dependency(Module(org"acme", "playy"), "2.4.0")
        val res = await(resolve0(
          Set(dep)
        ))

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope),
          errorCache = Map(dep.moduleVersion -> Seq("Not found"))
        )

        assert(res == expected)
      }
    }
    'missingPom{
      async {
        val dep = Dependency(Module(org"acme", "module-with-missing-pom"), "1.0.0")
        val res = await(resolve0(
          Set(dep)
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
        assert(res.errors == Seq((Module(org"acme", "missing-pom"), "1.0.0") -> List("Not found")))
      }
    }
    'single{
      async {
        val dep = Dependency(Module(org"acme", "config"), "1.3.0")
        val res = await(resolve0(
          Set(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope),
          projectCache = Map(dep.moduleVersion -> (testRepository, projectsMap(dep.moduleVersion)))
        )

        assert(res == expected)
      }
    }
    'oneTransitiveDependency{
      async {
        val dep = Dependency(Module(org"acme", "play"), "2.4.0")
        val trDep = Dependency(Module(org"acme", "play-json"), "2.4.0")
        val res = await(resolve0(
          Set(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope, trDep.withCompileScope),
          projectCache = Map(
            projectsMap(dep.moduleVersion).kv,
            projectsMap(trDep.moduleVersion).kv
          )
        )

        assert(res == expected)
      }
    }
    'twoTransitiveDependencyWithProps{
      async {
        val dep = Dependency(Module(org"acme", "play"), "2.4.1")
        val trDeps = Seq(
          Dependency(Module(org"acme", "play-json"), "2.4.0"),
          Dependency(Module(org"acme", "config"), "1.3.0")
        )
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'exclude{
      async {
        val dep = Dependency(Module(org"acme", "play-extra-no-config"), "2.4.1")
        val trDeps = Seq(
          Dependency(Module(org"acme", "play"), "2.4.1",
            exclusions = Set((org"acme", "config"))),
          Dependency(Module(org"acme", "play-json"), "2.4.0",
            exclusions = Set((org"acme", "config")))
        )
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'excludeOrgWildcard{
      async {
        val dep = Dependency(Module(org"acme", "play-extra-no-config-no"), "2.4.1")
        val trDeps = Seq(
          Dependency(Module(org"acme", "play"), "2.4.1",
            exclusions = Set((org"*", "config"))),
          Dependency(Module(org"acme", "play-json"), "2.4.0",
            exclusions = Set((org"*", "config")))
        )
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'filter{
      async {
        val dep = Dependency(Module(org"hudsucker", "mail"), "10.0")
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'parentDepMgmt{
      async {
        val dep = Dependency(Module(org"se.ikea", "billy"), "18.0")
        val trDeps = Seq(
          Dependency(Module(org"acme", "play"), "2.4.0",
            exclusions = Set((org"acme", "play-json")))
        )
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'parentDependencies{
      async {
        val dep = Dependency(Module(org"org.gnome", "panel-legacy"), "7.0")
        val trDeps = Seq(
          Dependency(Module(org"org.gnu", "glib"), "13.4"),
          Dependency(Module(org"org.gnome", "desktop"), "7.0"))
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'propertiesInExclusions{
      async {
        val dep = Dependency(Module(org"com.mailapp", "mail-client"), "2.1")
        val trDeps = Seq(
          Dependency(Module(org"gov.nsa", "secure-pgp"), "10.0", exclusions = Set((org"*", "crypto"))))
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'depMgmtInParentDeps{
      async {
        val dep = Dependency(Module(org"com.thoughtworks.paranamer", "paranamer"), "2.6")
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'depsFromDefaultProfile{
      async {
        val dep = Dependency(Module(org"com.github.dummy", "libb"), "0.3.3")
        val trDeps = Seq(
          Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'depsFromPropertyActivatedProfile{
      val f =
        for (version <- Seq("0.5.3", "0.5.4", "0.5.5", "0.5.6", "0.5.8")) yield {
          async {
            val dep = Dependency(Module(org"com.github.dummy", "libb"), version)
            val trDeps = Seq(
              Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))
            val res = await(resolve0(
              Set(dep)
            )).clearCaches

            val expected = Resolution(
              rootDependencies = Set(dep),
              dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
            )

            assert(res == expected)
          }
        }

      scala.concurrent.Future.sequence(f)
    }
    'depsFromProfileDisactivatedByPropertyAbsence{
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
        val dep = Dependency(Module(org"com.github.dummy", "libb"), "0.5.7")
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope)
        )

        assert(res == expected)
      }
    }
    'depsScopeOverrideFromProfile{
      async {
        // Like com.google.inject:guice:3.0 with org.sonatype.sisu.inject:cglib
        val dep = Dependency(Module(org"com.github.dummy", "libb"), "0.4.2")
        val trDeps = Seq(
          Dependency(Module(org"org.escalier", "librairie-standard"), "2.11.6"))
        val res = await(resolve0(
          Set(dep)
        )).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }

    'exclusionsAndOptionalShouldGoAlong{
      async {
        val dep = Dependency(Module(org"an-org", "an-app"), "1.0")
        val trDeps = Seq(
          Dependency(Module(org"an-org", "a-lib"), "1.0", exclusions = Set((org"an-org", "a-name"))),
          Dependency(Module(org"an-org", "another-lib"), "1.0", optional = true),
          Dependency(Module(org"an-org", "a-name"), "1.0", optional = true))
        val res = await(resolve0(
          Set(dep),
          filter = Some(_ => true)
        )).clearCaches.clearFilter

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }

    'exclusionsOfDependenciesFromDifferentPathsShouldNotCollide{
      async {
        val deps = Set(
          Dependency(Module(org"an-org", "an-app"), "1.0"),
          Dependency(Module(org"an-org", "a-lib"), "1.0", optional = true))
        val trDeps = Seq(
          Dependency(Module(org"an-org", "a-lib"), "1.0", exclusions = Set((org"an-org", "a-name"))),
          Dependency(Module(org"an-org", "another-lib"), "1.0", optional = true),
          Dependency(Module(org"an-org", "a-name"), "1.0", optional = true))
        val res = await(resolve0(
          deps,
          filter = Some(_ => true)
        )).clearCaches.clearFilter

        val expected = Resolution(
          rootDependencies = deps,
          dependencies = (deps ++ trDeps).map(_.withCompileScope)
        )

        assert(res == expected)
      }
    }

    'dependencyOverrides - {
      * - {
        async {
          val deps = Set(
            Dependency(Module(org"an-org", "a-name"), "1.1"))
          val depOverrides = Map(
            Module(org"an-org", "a-name") -> "1.0")

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution(
            rootDependencies = deps,
            dependencies = Set(
              Dependency(Module(org"an-org", "a-name"), "1.0")
            ).map(_.withCompileScope),
            forceVersions = depOverrides
          )

          assert(res == expected)
        }
      }

      * - {
        async {
          val deps = Set(
            Dependency(Module(org"an-org", "an-app"), "1.1"))
          val depOverrides = Map(
            Module(org"an-org", "a-lib") -> "1.0")

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution(
            rootDependencies = deps,
            dependencies = Set(
              Dependency(Module(org"an-org", "an-app"), "1.1"),
              Dependency(Module(org"an-org", "a-lib"), "1.0"),
              Dependency(Module(org"an-org", "a-name"), "1.0")
            ).map(_.withCompileScope),
            forceVersions = depOverrides
          )

          assert(res == expected)
        }
      }

      * - {
        async {
          val deps = Set(
            Dependency(Module(org"an-org", "an-app"), "1.2"))
          val depOverrides = Map(
            Module(org"an-org", "a-lib") -> "1.1")

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution(
            rootDependencies = deps,
            dependencies = Set(
              Dependency(Module(org"an-org", "an-app"), "1.2"),
              Dependency(Module(org"an-org", "a-lib"), "1.1")
            ).map(_.withCompileScope),
            forceVersions = depOverrides
          )

          assert(res == expected)
        }
      }
    }

    'parts{
      'propertySubstitution{
        val res =
          core.Resolution.withProperties(
            Seq("" -> Dependency(Module(org"a-company", "a-name"), "${a.property}")),
            Map("a.property" -> "a-version"))
        val expected = Seq("" -> Dependency(Module(org"a-company", "a-name"), "a-version"))

        assert(res == expected)
      }
    }

    'forcedProperties - {
      async {
        val deps = Set(
          Dependency(Module(org"com.github.dummy", "libb"), "0.5.4")
        )

        val forceProperties = Map(
          "special" -> "false"
        )

        val res = await(
          resolve0(deps, forceProperties = forceProperties)
        ).clearCaches

        val expected = Resolution(
          rootDependencies = deps,
          dependencies = Set(
            Dependency(Module(org"com.github.dummy", "libb"), "0.5.4")
          ).map(_.withCompileScope),
          forceProperties = forceProperties
        )

        assert(res == expected)
      }
    }
  }

}
