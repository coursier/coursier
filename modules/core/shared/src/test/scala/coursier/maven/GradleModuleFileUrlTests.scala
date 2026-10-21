package coursier.maven

import coursier.core.{
  Dependency,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Publication,
  VariantSelector
}
import coursier.util.{Artifact, EitherT, Monad}
import coursier.version.{Version, VersionConstraint}
import utest._

object GradleModuleFileUrlTests extends TestSuite {

  private type Id[A] = A
  private implicit val idMonad: Monad[Id] =
    new Monad[Id] {
      def point[A](a: A): A                 = a
      def bind[A, B](elem: A)(f: A => B): B = f(elem)
    }

  private val root   = "https://repo.example.com/maven2"
  private val dir    = Seq("org", "example", "lib", "1.0")
  private val module = Module(Organization("org.example"), ModuleName("lib"), Map.empty)

  private def moduleFile(fileUrl: String): String =
    s"""{
       |  "formatVersion": "1.1",
       |  "component": { "group": "org.example", "module": "lib", "version": "1.0" },
       |  "variants": [
       |    {
       |      "name": "runtimeElements",
       |      "attributes": { "org.gradle.usage": "java-runtime" },
       |      "files": [
       |        { "name": "lib-1.0.jar", "url": "$fileUrl", "size": 1, "sha256": "00" }
       |      ]
       |    }
       |  ]
       |}
       |""".stripMargin

  /** Resolves org.example:lib:1.0 against an in-memory repository serving a module file whose
    * single file has the passed URL, and returns the error or its module artifacts.
    */
  private def moduleArtifactUrls(fileUrl: String): Either[String, Seq[Artifact]] = {
    val repo      = MavenRepository(root).copy(checkModule = true)
    val moduleUrl = s"$root/${dir.mkString("/")}/lib-1.0.module"
    val fetch: Artifact => EitherT[Id, String, String] = { artifact =>
      EitherT[Id, String, String] {
        if (artifact.url == moduleUrl) Right(moduleFile(fileUrl))
        else Left(s"not found: ${artifact.url}")
      }
    }
    repo.find0(module, Version("1.0"), fetch).run.map {
      case (_, project) =>
        val dep = Dependency(
          module,
          VersionConstraint("1.0"),
          VariantSelector.AttributesBased(Map.empty),
          MinimizedExclusions.zero,
          Publication.empty,
          optional = false,
          transitive = true
        )
        repo
          .moduleArtifacts(dep, project, Some(VariantSelector.AttributesBased.empty))
          .map(_._2)
    }
  }

  val tests = Tests {

    test("resolveModuleFileUrl") {
      def resolve(url: String) = MavenRepositoryInternal.resolveModuleFileUrl(dir, url)

      test("accepted") {
        assert(resolve("lib-1.0.jar") == Right(dir :+ "lib-1.0.jar"))
        assert(resolve("./lib-1.0.jar") == Right(dir :+ "lib-1.0.jar"))
        assert(resolve("sub/lib-1.0.jar") == Right(dir ++ Seq("sub", "lib-1.0.jar")))
        // sibling version directory, still within the repository
        assert(
          resolve("../0.9/lib-0.9.jar") == Right(Seq("org", "example", "lib", "0.9", "lib-0.9.jar"))
        )
        assert(resolve("../../../../top.jar") == Right(Seq("top.jar")))
        assert(resolve("lib%201.0.jar") == Right(dir :+ "lib%201.0.jar"))
      }

      test("rejected") {
        val invalid = Seq(
          "",
          "https://evil.example.com/lib-1.0.jar",
          "http://localhost:8080/lib-1.0.jar",
          "file:///home/u/.aws/credentials",
          "file:lib-1.0.jar",
          "jar:file:/x.jar!/a",
          "C:/Windows/win.ini",
          "//evil.example.com/lib-1.0.jar",
          "/etc/passwd",
          "../../../../../etc/passwd",
          "sub/../../../../../../x.jar",
          "%2e%2e/%2e%2e/%2E%2E/%2e%2e/%2e%2e/x.jar",
          "..%2f..%2f..%2f..%2f..%2fx.jar",
          "..\\..\\x.jar",
          "lib-1.0.jar?x=1",
          "lib-1.0.jar#x",
          "a//lib-1.0.jar",
          "sub/",
          ".",
          ".."
        )
        for (url <- invalid) {
          val res = resolve(url)
          assert(res.isLeft)
        }
      }
    }

    test("module file") {
      test("relative") {
        val res       = moduleArtifactUrls("lib-1.0.jar")
        val artifacts = res.toOption.get
        assert(artifacts.map(_.url) == Seq(s"$root/org/example/lib/1.0/lib-1.0.jar"))
        val checksumUrls = artifacts.head.checksumUrls
        assert(checksumUrls.get("SHA-1").contains(s"$root/org/example/lib/1.0/lib-1.0.jar.sha1"))
      }

      test("sibling directory") {
        val res = moduleArtifactUrls("../0.9/lib-0.9.jar")
        assert(res.map(_.map(_.url)) == Right(Seq(s"$root/org/example/lib/0.9/lib-0.9.jar")))
      }

      test("absolute URL") {
        val res = moduleArtifactUrls("https://evil.example.com/lib-1.0.jar")
        assert(res.left.exists(_.contains("Invalid file URL")))
      }

      test("local file") {
        val res = moduleArtifactUrls("file:///home/u/.aws/credentials")
        assert(res.left.exists(_.contains("Invalid file URL")))
      }

      test("outside of the repository") {
        val res = moduleArtifactUrls("../../../../../../home/u/.aws/credentials")
        assert(res.left.exists(_.contains("Invalid file URL")))
      }
    }
  }
}
