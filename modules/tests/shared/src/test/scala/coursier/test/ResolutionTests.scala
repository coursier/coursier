package coursier
package test

import coursier.core.{Activation, Configuration, Repository}
import coursier.maven.MavenRepository
import utest._
import scala.async.Async.{async, await}

import coursier.core
import coursier.test.compatibility._

object ResolutionTests extends TestSuite {

  def resolve0(
    deps: Seq[Dependency],
    filter: Option[Dependency => Boolean] = None,
    forceVersions: Map[Module, String] = Map.empty,
    forceProperties: Map[String, String] = Map.empty
  ) =
    Resolution()
      .withRootDependencies(deps)
      .withFilter(filter)
      .withForceVersions(forceVersions)
      .withForceProperties(forceProperties)
      .withOsInfo(Activation.Os.empty)
      .process
      .run(Platform.fetch(repositories))
      .future()

  implicit class ProjectOps(val p: Project) extends AnyVal {
    def kv: (ModuleVersion, (Artifact.Source, Project)) = p.moduleVersion -> (testRepository, p)
  }

  val projects = Seq(
    Project(mod"acme:config", "1.3.0"),

    Project(mod"acme:play", "2.4.0", Seq(
      Configuration.empty -> dep"acme:play-json:2.4.0")),

    Project(Module(org"acme", name"play-json"), "2.4.0"),

    Project(Module(org"acme", name"play"), "2.4.1",
      dependencies = Seq(
        Configuration.empty -> Dependency(Module(org"acme", name"play-json"), "${play_json_version}"),
        Configuration.empty -> Dependency(Module(org"$${project.groupId}", name"$${WithSpecialChar©}"), "1.3.0")),
      properties = Seq(
        "play_json_version" -> "2.4.0",
        "WithSpecialChar©" -> "config")),

    Project(Module(org"acme", name"play-extra-no-config"), "2.4.1",
      Seq(
        Configuration.empty -> Dependency(Module(org"acme", name"play"), "2.4.1",
          exclusions = Set((org"acme", name"config"))))),

    Project(Module(org"acme", name"play-extra-no-config-no"), "2.4.1",
      Seq(
        Configuration.empty -> Dependency(Module(org"acme", name"play"), "2.4.1",
          exclusions = Set((org"*", name"config"))))),

    Project(Module(org"acme", name"module-with-missing-pom"), "1.0.0",
      dependencyManagement = Seq(
        Configuration.`import` -> Dependency(Module(org"acme", name"missing-pom"), "1.0.0"))),

    Project(Module(org"hudsucker", name"mail"), "10.0",
      Seq(
        Configuration.test -> Dependency(Module(org"$${project.groupId}", name"test-util"), "${project.version}"))),

    Project(Module(org"hudsucker", name"test-util"), "10.0"),

    Project(Module(org"se.ikea", name"parent"), "18.0",
      dependencyManagement = Seq(
        Configuration.empty -> Dependency(Module(org"acme", name"play"), "2.4.0",
          exclusions = Set((org"acme", name"play-json"))))),

    Project(Module(org"se.ikea", name"billy"), "18.0",
      dependencies = Seq(
        Configuration.empty -> Dependency(Module(org"acme", name"play"), "")),
      parent = Some(Module(org"se.ikea", name"parent"), "18.0")),

    Project(Module(org"org.gnome", name"parent"), "7.0",
      Seq(
        Configuration.empty -> Dependency(Module(org"org.gnu", name"glib"), "13.4"))),

    Project(Module(org"org.gnome", name"panel-legacy"), "7.0",
      dependencies = Seq(
        Configuration.empty -> Dependency(Module(org"org.gnome", name"desktop"), "${project.version}")),
      parent = Some(Module(org"org.gnome", name"parent"), "7.0")),

    Project(Module(org"gov.nsa", name"secure-pgp"), "10.0",
      Seq(
        Configuration.empty -> Dependency(Module(org"gov.nsa", name"crypto"), "536.89"))),

    Project(Module(org"com.mailapp", name"mail-client"), "2.1",
      dependencies = Seq(
        Configuration.empty -> Dependency(Module(org"gov.nsa", name"secure-pgp"), "10.0",
          exclusions = Set((org"*", name"$${crypto.name}")))),
      properties = Seq("crypto.name" -> "crypto", "dummy" -> "2")),

    Project(Module(org"com.thoughtworks.paranamer", name"paranamer-parent"), "2.6",
      dependencies = Seq(
        Configuration.empty -> Dependency(Module(org"junit", name"junit"), "")),
      dependencyManagement = Seq(
        Configuration.test -> Dependency(Module(org"junit", name"junit"), "4.11"))),

    Project(Module(org"com.thoughtworks.paranamer", name"paranamer"), "2.6",
      parent = Some(Module(org"com.thoughtworks.paranamer", name"paranamer-parent"), "2.6")),

    Project(Module(org"com.github.dummy", name"libb"), "0.3.3",
      profiles = Seq(
        Profile("default", activeByDefault = Some(true), dependencies = Seq(
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", name"libb"), "0.4.2",
      dependencies = Seq(
        Configuration.empty -> Dependency(Module(org"org.scalaverification", name"scala-verification"), "1.12.4")),
      profiles = Seq(
        Profile("default", activeByDefault = Some(true), dependencies = Seq(
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"),
          Configuration.test -> Dependency(Module(org"org.scalaverification", name"scala-verification"), "1.12.4"))))),

    Project(Module(org"com.github.dummy", name"libb"), "0.5.3",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> None)), dependencies = Seq(
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", name"libb"), "0.5.4",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("true"))), dependencies = Seq(
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", name"libb"), "0.5.5",
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("!false"))), dependencies = Seq(
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", name"libb-parent"), "0.5.6",
      properties = Seq("special" -> "true")),

    Project(Module(org"com.github.dummy", name"libb"), "0.5.6",
      parent = Some(Module(org"com.github.dummy", name"libb-parent"), "0.5.6"),
      properties = Seq("special" -> "true"),
      profiles = Seq(
        Profile("default", activation = Profile.Activation(properties = Seq("special" -> Some("!false"))), dependencies = Seq(
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", name"libb"), "0.5.7",
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
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"com.github.dummy", name"libb"), "0.5.8",
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
          Configuration.empty -> Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))))),

    Project(Module(org"an-org", name"a-name"), "1.0"),

    Project(Module(org"an-org", name"a-name"), "1.2"),

    Project(Module(org"an-org", name"a-lib"), "1.0",
      Seq(Configuration.empty -> Dependency(Module(org"an-org", name"a-name"), "1.0"))),

    Project(Module(org"an-org", name"a-lib"), "1.1"),

    Project(Module(org"an-org", name"a-lib"), "1.2",
      Seq(Configuration.empty -> Dependency(Module(org"an-org", name"a-name"), "1.2"))),

    Project(Module(org"an-org", name"another-lib"), "1.0",
      Seq(Configuration.empty -> Dependency(Module(org"an-org", name"a-name"), "1.0"))),

    // Must bring transitively an-org:a-name, as an optional dependency
    Project(Module(org"an-org", name"an-app"), "1.0",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"a-lib"), "1.0", exclusions = Set((org"an-org", name"a-name"))),
        Configuration.empty -> Dependency(Module(org"an-org", name"another-lib"), "1.0", optional = true))),

    Project(Module(org"an-org", name"an-app"), "1.1",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"a-lib"), "1.1"))),

    Project(Module(org"an-org", name"an-app"), "1.2",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"a-lib"), "1.2"))),

    Project(Module(org"an-org", name"my-lib-1"), "1.0.0+build.027",
      Seq()),

    Project(Module(org"an-org", name"my-lib-1"), "1.1.0+build.018",
      Seq()),

    Project(Module(org"an-org", name"my-lib-1"), "1.2.0",
      Seq()),

    Project(Module(org"an-org", name"my-lib-2"), "1.0",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-1"), "1.0.0+build.027"))),

    Project(Module(org"an-org", name"my-lib-3"), "1.0",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-1"), "1.1.0+build.018"))),

    Project(Module(org"an-org", name"my-lib-3"), "1.1",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-1"), "1.2.0"))),

    Project(Module(org"an-org", name"my-app"), "1.0",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-2"), "1.0"),
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-3"), "1.0"))),

    Project(Module(org"an-org", name"my-app"), "1.1",
      Seq(
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-2"), "1.0"),
        Configuration.empty -> Dependency(Module(org"an-org", name"my-lib-3"), "1.1")))

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
          Nil
        ))

        assert(res == Resolution.empty)
      }
    }
    'notFound{
      async {
        val dep = Dependency(Module(org"acme", name"playy"), "2.4.0")
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
    'missingPom{
      async {
        val dep = Dependency(Module(org"acme", name"module-with-missing-pom"), "1.0.0")
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
        assert(res.errors == Seq((Module(org"acme", name"missing-pom"), "1.0.0") -> List("Not found")))
      }
    }
    'single{
      async {
        val dep = Dependency(Module(org"acme", name"config"), "1.3.0")
        val res = await(resolve0(
          Seq(dep)
        )).clearFinalDependenciesCache.clearProjectProperties

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))
          .withProjectCache(Map(dep.moduleVersion -> (testRepository, projectsMap(dep.moduleVersion))))

        assert(res == expected)
      }
    }
    'oneTransitiveDependency{
      async {
        val dep = Dependency(Module(org"acme", name"play"), "2.4.0")
        val trDep = Dependency(Module(org"acme", name"play-json"), "2.4.0")
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
    'twoTransitiveDependencyWithProps{
      async {
        val dep = Dependency(Module(org"acme", name"play"), "2.4.1")
        val trDeps = Seq(
          Dependency(Module(org"acme", name"play-json"), "2.4.0"),
          Dependency(Module(org"acme", name"config"), "1.3.0")
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
    'exclude{
      async {
        val dep = Dependency(Module(org"acme", name"play-extra-no-config"), "2.4.1")
        val trDeps = Seq(
          Dependency(Module(org"acme", name"play"), "2.4.1",
            exclusions = Set((org"acme", name"config"))),
          Dependency(Module(org"acme", name"play-json"), "2.4.0",
            exclusions = Set((org"acme", name"config")))
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
    'excludeOrgWildcard{
      async {
        val dep = Dependency(Module(org"acme", name"play-extra-no-config-no"), "2.4.1")
        val trDeps = Seq(
          Dependency(Module(org"acme", name"play"), "2.4.1",
            exclusions = Set((org"*", name"config"))),
          Dependency(Module(org"acme", name"play-json"), "2.4.0",
            exclusions = Set((org"*", name"config")))
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
    'filter{
      async {
        val dep = Dependency(Module(org"hudsucker", name"mail"), "10.0")
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))

        assert(res == expected)
      }
    }
    'parentDepMgmt{
      async {
        val dep = Dependency(Module(org"se.ikea", name"billy"), "18.0")
        val trDeps = Seq(
          Dependency(Module(org"acme", name"play"), "2.4.0",
            exclusions = Set((org"acme", name"play-json")))
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
    'parentDependencies{
      async {
        val dep = Dependency(Module(org"org.gnome", name"panel-legacy"), "7.0")
        val trDeps = Seq(
          Dependency(Module(org"org.gnu", name"glib"), "13.4"),
          Dependency(Module(org"org.gnome", name"desktop"), "7.0"))
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    'propertiesInExclusions{
      async {
        val dep = Dependency(Module(org"com.mailapp", name"mail-client"), "2.1")
        val trDeps = Seq(
          Dependency(Module(org"gov.nsa", name"secure-pgp"), "10.0", exclusions = Set((org"*", name"crypto"))))
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    'depMgmtInParentDeps{
      async {
        val dep = Dependency(Module(org"com.thoughtworks.paranamer", name"paranamer"), "2.6")
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))

        assert(res == expected)
      }
    }
    'depsFromDefaultProfile{
      async {
        val dep = Dependency(Module(org"com.github.dummy", name"libb"), "0.3.3")
        val trDeps = Seq(
          Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }
    'depsFromPropertyActivatedProfile{
      val f =
        for (version <- Seq("0.5.3", "0.5.4", "0.5.5", "0.5.6", "0.5.8")) yield {
          async {
            val dep = Dependency(Module(org"com.github.dummy", name"libb"), version)
            val trDeps = Seq(
              Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))
            val res = await(resolve0(
              Seq(dep)
            )).clearCaches

            val expected = Resolution()
              .withRootDependencies(Seq(dep))
              .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

            assert(res == expected)
          }
        }

      scala.concurrent.Future.sequence(f).map(_ => ())
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
        val dep = Dependency(Module(org"com.github.dummy", name"libb"), "0.5.7")
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope))

        assert(res == expected)
      }
    }
    'depsScopeOverrideFromProfile{
      async {
        // Like com.google.inject:guice:3.0 with org.sonatype.sisu.inject:cglib
        val dep = Dependency(Module(org"com.github.dummy", name"libb"), "0.4.2")
        val trDeps = Seq(
          Dependency(Module(org"org.escalier", name"librairie-standard"), "2.11.6"))
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }
    }

    'exclusionsAndOptionalShouldGoAlong{
      async {
        val dep = Dependency(Module(org"an-org", name"an-app"), "1.0")
        val trDeps = Seq(
          Dependency(Module(org"an-org", name"a-lib"), "1.0", exclusions = Set((org"an-org", name"a-name"))),
          Dependency(Module(org"an-org", name"another-lib"), "1.0", optional = true),
          Dependency(Module(org"an-org", name"a-name"), "1.0", optional = true))
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

    'exclusionsOfDependenciesFromDifferentPathsShouldNotCollide{
      async {
        val deps = Seq(
          Dependency(Module(org"an-org", name"an-app"), "1.0"),
          Dependency(Module(org"an-org", name"a-lib"), "1.0", optional = true))
        val trDeps = Seq(
          Dependency(Module(org"an-org", name"a-lib"), "1.0", exclusions = Set((org"an-org", name"a-name"))),
          Dependency(Module(org"an-org", name"another-lib"), "1.0", optional = true),
          Dependency(Module(org"an-org", name"a-name"), "1.0", optional = true))
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

    'dependencyOverrides - {
      * - {
        async {
          val deps = Seq(
            Dependency(Module(org"an-org", name"a-name"), "1.1"))
          val depOverrides = Map(
            Module(org"an-org", name"a-name") -> "1.0")

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(deps)
            .withDependencies(
              Set(
                Dependency(Module(org"an-org", name"a-name"), "1.0")
              ).map(_.withCompileScope)
            )
            .withForceVersions(depOverrides)

          assert(res == expected)
        }
      }

      * - async {
        val deps = Seq(
          Dependency(Module(org"an-org", name"an-app"), "1.1"))
        val depOverrides = Map(
          Module(org"an-org", name"a-lib") -> "1.0")

        val res = await(resolve0(
          deps,
          forceVersions = depOverrides
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              Dependency(Module(org"an-org", name"an-app"), "1.1"),
              Dependency(Module(org"an-org", name"a-lib"), "1.0"),
              Dependency(Module(org"an-org", name"a-name"), "1.0")
            ).map(_.withCompileScope)
          )
          .withForceVersions(depOverrides)

        assert(res == expected)
      }

      * - async {
        val deps = Seq(
          Dependency(Module(org"an-org", name"an-app"), "1.1"))
        val depOverrides = Map(
          Module(org"*", name"a-lib") -> "1.0")

        val res = await(resolve0(
          deps,
          forceVersions = depOverrides
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(deps)
          .withDependencies(
            Set(
              Dependency(Module(org"an-org", name"an-app"), "1.1"),
              Dependency(Module(org"an-org", name"a-lib"), "1.0"),
              Dependency(Module(org"an-org", name"a-name"), "1.0")
            ).map(_.withCompileScope)
          )
          .withForceVersions(depOverrides)

        assert(res == expected)
      }

      * - {
        async {
          val deps = Seq(
            Dependency(Module(org"an-org", name"an-app"), "1.2"))
          val depOverrides = Map(
            Module(org"an-org", name"a-lib") -> "1.1")

          val res = await(resolve0(
            deps,
            forceVersions = depOverrides
          )).clearCaches

          val expected = Resolution()
            .withRootDependencies(deps)
            .withDependencies(
              Set(
                Dependency(Module(org"an-org", name"an-app"), "1.2"),
                Dependency(Module(org"an-org", name"a-lib"), "1.1")
              ).map(_.withCompileScope)
            )
            .withForceVersions(depOverrides)

          assert(res == expected)
        }
      }
    }

    'parts{
      'propertySubstitution{
        val res =
          core.Resolution.withProperties(
            Seq(Configuration.empty -> Dependency(Module(org"a-company", name"a-name"), "${a.property}")),
            Map("a.property" -> "a-version"))
        val expected = Seq(Configuration.empty -> Dependency(Module(org"a-company", name"a-name"), "a-version"))

        assert(res == expected)
      }
    }

    'forcedProperties - {
      async {
        val deps = Seq(
          Dependency(Module(org"com.github.dummy", name"libb"), "0.5.4")
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
              Dependency(Module(org"com.github.dummy", name"libb"), "0.5.4")
            ).map(_.withCompileScope)
          )
          .withForceProperties(forceProperties)

        assert(res == expected)
      }
    }

    'mergingTransitiveDeps - {
      * - async {
        val dep = Dependency(Module(org"an-org", name"my-app"), "1.0")
        val trDeps = Seq(
          Dependency(Module(org"an-org", name"my-lib-1"), "1.1.0+build.018"),
          Dependency(Module(org"an-org", name"my-lib-2"), "1.0"),
          Dependency(Module(org"an-org", name"my-lib-3"), "1.0")
        )
        val res = await(resolve0(
          Seq(dep)
        )).clearCaches

        val expected = Resolution()
          .withRootDependencies(Seq(dep))
          .withDependencies(Set(dep.withCompileScope) ++ trDeps.map(_.withCompileScope))

        assert(res == expected)
      }

      * - async {
        val dep = Dependency(Module(org"an-org", name"my-app"), "1.1")
        val trDeps = Seq(
          Dependency(Module(org"an-org", name"my-lib-1"), "1.2.0"),
          Dependency(Module(org"an-org", name"my-lib-2"), "1.0"),
          Dependency(Module(org"an-org", name"my-lib-3"), "1.1")
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
