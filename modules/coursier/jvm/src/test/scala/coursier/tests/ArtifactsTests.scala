package coursier.tests

import coursier.{Artifacts, Repositories, Resolve}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.util.InMemoryRepository
import coursier.util.StringInterpolators._
import coursier.version.{Version, VersionConstraint}
import utest._

import scala.async.Async.{async, await}

object ArtifactsTests extends TestSuite {

  import TestHelpers.{ec, cache, handmadeMetadataBase}

  val tests = Tests {

    test("severalResolutions") {
      async {

        val res1 = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"io.argonaut:argonaut_2.12:6.2.2")
            .withCache(cache)
            .future()
        }

        val res2 = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.chuusai:shapeless_2.12:2.3.2")
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolutions(Seq(res1, res2))
            .withCache(cache)
            .future()
        }

        val urls = artifacts.map(_._1.url).distinct.sorted

        val expectedArgonautUrls = Seq(
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.6/scala-library-2.12.6.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.6/scala-reflect-2.12.6.jar",
          "https://repo1.maven.org/maven2/io/argonaut/argonaut_2.12/6.2.2/argonaut_2.12-6.2.2.jar"
        )

        val expectedShapelessUrls = Seq(
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar",
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.2/shapeless_2.12-2.3.2.jar",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar"
        )

        val expectedUrls = (expectedArgonautUrls ++ expectedShapelessUrls).distinct.sorted

        assert(urls == expectedUrls)
      }
    }

    test("extraArtifacts") {
      async {

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.chuusai:shapeless_2.12:2.3.2")
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolution(res)
            .withCache(cache)
            .addExtraArtifacts { l =>
              l.flatMap {
                case (_, _, a) =>
                  a.extra.get("sig").toSeq
              }
            }
            .future()
        }

        val urls = artifacts.map(_._1.url).distinct.sorted

        val expectedUrls = Seq(
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.2/shapeless_2.12-2.3.2.jar",
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.2/shapeless_2.12-2.3.2.jar.asc",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar.asc",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar.asc"
        )

        assert(urls == expectedUrls)
      }
    }

    test("transformArtifacts") {
      async {

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.chuusai:shapeless_2.12:2.3.2")
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolution(res)
            .withCache(cache)
            .addTransformArtifacts { l =>
              l.flatMap {
                case elem @ (d, p, a) =>
                  val extra = a.extra.get("sig").toSeq.map(a0 => (d, p, a0))
                  elem +: extra
              }
            }
            .future()
        }

        val urls = artifacts.map(_._1.url).distinct.sorted

        val expectedUrls = Seq(
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.2/shapeless_2.12-2.3.2.jar",
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.2/shapeless_2.12-2.3.2.jar.asc",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar.asc",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar.asc"
        )

        assert(urls == expectedUrls)
      }
    }

    test("noDuplicatedArtifacts") {
      async {

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.chuusai:shapeless_2.12:2.3.2")
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolutions(Seq(res, res))
            .withCache(cache)
            .future()
        }

        val urls = artifacts.map(_._1.url).sorted

        val expectedUrls = Seq(
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.2/shapeless_2.12-2.3.2.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar"
        )

        assert(urls == expectedUrls)
      }
    }

    test("no two versions of a dependency") {
      async {

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(
              dep"io.get-coursier:coursier-core_2.12:2.0.0-RC6",
              dep"io.get-coursier:coursier_2.12:2.0.0-RC6-16"
            )
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolution(res)
            .withCache(cache)
            .future()
        }

        val urls = artifacts.map(_._1.url)

        // FIXME I don't like that order… scala-library should be last, as most others depend on it, so
        // should appear *before* it on the class path.
        val expectedUrls = Seq(
          "https://repo1.maven.org/maven2/io/get-coursier/coursier-core_2.12/2.0.0-RC6-16/coursier-core_2.12-2.0.0-RC6-16.jar",
          "https://repo1.maven.org/maven2/io/get-coursier/coursier_2.12/2.0.0-RC6-16/coursier_2.12-2.0.0-RC6-16.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.10/scala-library-2.12.10.jar",
          "https://repo1.maven.org/maven2/io/get-coursier/coursier-util_2.12/2.0.0-RC6-16/coursier-util_2.12-2.0.0-RC6-16.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.12/1.3.0/scala-xml_2.12-1.3.0.jar",
          "https://repo1.maven.org/maven2/io/get-coursier/coursier-cache_2.12/2.0.0-RC6-16/coursier-cache_2.12-2.0.0-RC6-16.jar",
          "https://repo1.maven.org/maven2/com/github/alexarchambault/argonaut-shapeless_6.2_2.12/1.2.0-M12/argonaut-shapeless_6.2_2.12-1.2.0-M12.jar",
          "https://repo1.maven.org/maven2/io/github/alexarchambault/windows-ansi/windows-ansi/0.0.3/windows-ansi-0.0.3.jar",
          "https://repo1.maven.org/maven2/io/argonaut/argonaut_2.12/6.2.4/argonaut_2.12-6.2.4.jar",
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar",
          "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/1.18/jansi-1.18.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.10/scala-reflect-2.12.10.jar",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar"
        )

        assert(urls == expectedUrls)
      }
    }

    test("in memory repo") {
      async {

        val wrongShapelessJarUrl =
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.11/2.3.242/shapeless_2.11-2.3.242.jar"
        val inMemoryRepo = InMemoryRepository.privateApply(Map(
          (mod"com.chuusai:shapeless_2.11", Version("2.3.3")) ->
            (new java.net.URL(wrongShapelessJarUrl), false)
        ))

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.chuusai:shapeless_2.11:2.3.3")
            .withRepositories(Seq(
              inMemoryRepo,
              Repositories.central
            ))
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolutions(Seq(res))
            .withCache(cache)
            .future()
        }

        val urls = artifacts.map(_._1.url).sorted

        val expectedUrls = Seq(
          "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.11/2.3.3/shapeless_2.11-2.3.3.jar",
          "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.12/scala-library-2.11.12.jar",
          "https://repo1.maven.org/maven2/org/typelevel/macro-compat_2.11/1.1.1/macro-compat_2.11-1.1.1.jar"
        )

        assert(urls == expectedUrls)
      }
    }

    test("Take Ivy dependency artifacts into account") {
      "to maven" - async {

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.fake:lib1:1.7.27")
            .withRepositories(Seq(
              MavenRepository(handmadeMetadataBase + "/fake-maven"),
              IvyRepository.parse(
                handmadeMetadataBase +
                  "/fake-ivy/[defaultPattern]"
              ).fold(sys.error, identity)
            ))
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolutions(Seq(res))
            .withCache(cache)
            .future()
        }

        val urls =
          artifacts.map(_._1.url.replace(handmadeMetadataBase, "file:///handmade-metadata/")).sorted

        val expectedUrls = Seq(
          "file:///handmade-metadata//fake-ivy/com.fake/lib1/1.7.27/jars/lib1.jar",
          "file:///handmade-metadata//fake-maven/com/fake/lib2/1.3.4/lib2-1.3.4-core.jar"
        )

        assert(urls == expectedUrls)
      }

      "to ivy" - async {

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(dep"com.fake:lib1:1.7.27")
            .withRepositories(Seq(
              IvyRepository.parse(
                handmadeMetadataBase +
                  "/fake-ivy/[defaultPattern]"
              ).fold(sys.error, identity)
            ))
            .withCache(cache)
            .future()
        }

        val artifacts = await {
          Artifacts()
            .withResolutions(Seq(res))
            .withCache(cache)
            .future()
        }

        val urls =
          artifacts.map(_._1.url.replace(handmadeMetadataBase, "file:///handmade-metadata/")).sorted

        val expectedUrls = Seq(
          "file:///handmade-metadata//fake-ivy/com.fake/lib1/1.7.27/jars/lib1.jar",
          "file:///handmade-metadata//fake-ivy/com.fake/lib2/1.3.4/jars/lib2-core.jar"
        )

        assert(urls == expectedUrls)
      }
    }

    "Don't group artifacts with same URL" - async {

      val res = await {
        Resolve()
          .noMirrors
          .addDependencies(dep"com.amazonaws:aws-java-sdk-s3:1.11.507")
          .withCache(cache)
          .future()
      }

      val databindUrl =
        "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.6.7.2/jackson-databind-2.6.7.2.jar"

      val artifacts = Artifacts.artifacts0(res, Set.empty, Nil, None, None, true).map(_._3).distinct
      val databindOpt = artifacts.find(_.url == databindUrl)
      assert(databindOpt.isDefined)
      val groupedArtifacts = Artifacts.groupArtifacts(artifacts :+ databindOpt.get)

      assert(groupedArtifacts.length == 2)

      val expectedDuplicatedUrls = Set(
        databindUrl
      )

      val firstGroupUrls = groupedArtifacts.head.map(_.url).toSet
      val duplicatedUrls = groupedArtifacts(1).map(_.url).toSet

      assert(duplicatedUrls == expectedDuplicatedUrls)
      assert((duplicatedUrls -- firstGroupUrls).isEmpty)
    }
  }

}
