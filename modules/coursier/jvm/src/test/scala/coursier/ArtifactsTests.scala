package coursier

import utest._

import scala.async.Async.{async, await}

object ArtifactsTests extends TestSuite {

  import TestHelpers.{ec, cache}

  val tests = Tests {

    'severalResolutions - async {

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

    'transformArtifacts - async {

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
          .transformArtifacts { l =>
            l.flatMap { a =>
              val sigOpt = a.extra.get("sig")
              Seq(a) ++ sigOpt.toSeq
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

    'noDuplicatedArtifacts - async {

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

}
