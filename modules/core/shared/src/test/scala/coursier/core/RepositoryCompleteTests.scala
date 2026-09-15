package coursier.core

import coursier.util.Monad
import coursier.version.{Version => Version0}
import utest._

import scala.collection.mutable

object RepositoryCompleteTests extends TestSuite {

  private type Id[A] = A
  private implicit val idMonad: Monad[Id] =
    new Monad[Id] {
      def point[A](a: A): A                 = a
      def bind[A, B](elem: A)(f: A => B): B = f(elem)
    }

  /** A Maven-like completer over an in-memory directory tree.
    *
    * Lists directories like `MavenComplete` does, records which ones it listed, and allows some of
    * them to be marked as non-listable, like the root of the Sonatype snapshot repositories.
    */
  private final class TestComplete(
    dirs: Map[Seq[String], Seq[String]],
    moduleVersions: Map[Seq[String], Seq[String]],
    notListable: Set[Seq[String]]
  ) extends Repository.Complete[Id] {

    val listed = mutable.Buffer.empty[Seq[String]]

    private def list(dir: Seq[String], prefix: String): Either[Throwable, Seq[String]] = {
      listed += dir
      if (notListable(dir))
        Left(new Exception(s"Directory listing forbidden: /${dir.mkString("/")}"))
      else
        dirs.get(dir) match {
          case None    => Left(new Exception(s"Not found: /${dir.mkString("/")}"))
          case Some(l) => Right(l.filter(_.startsWith(prefix)))
        }
    }

    def organization(prefix: String): Either[Throwable, Seq[String]] = {
      val idx = prefix.lastIndexOf('.')
      val (base, dir, prefix0) =
        if (idx < 0) ("", Nil, prefix)
        else (prefix.take(idx + 1), prefix.take(idx).split('.').toSeq, prefix.drop(idx + 1))
      list(dir, prefix0).map(_.map(base + _))
    }

    def moduleName(organization: Organization, prefix: String): Either[Throwable, Seq[String]] =
      list(organization.value.split('.').toSeq, prefix)

    protected def moduleDirectory(module: Module): String =
      module.name.value

    def versions(module: Module, prefix: String): Either[Throwable, Seq[Version0]] = {
      val dir = module.organization.value.split('.').toSeq :+ module.name.value
      moduleVersions.get(dir) match {
        case None    => Left(new Exception(s"Not found: /${dir.mkString("/")}"))
        case Some(l) => Right(l.filter(_.startsWith(prefix)).map(Version0(_)))
      }
    }
  }

  private val root: Seq[String] = Nil

  private def newComplete(notListable: Set[Seq[String]] = Set.empty): TestComplete =
    new TestComplete(
      dirs = Map(
        root                    -> Seq("com", "org"),
        Seq("org")              -> Seq("scala-lang", "scalameta"),
        Seq("org", "scalameta") -> Seq("metals_2.12", "metals_2.13"),
        Seq("com")              -> Seq("example"),
        Seq("com", "example")   -> Seq("thing")
      ),
      moduleVersions = Map(
        Seq("org", "scalameta", "metals_2.12") -> Seq("0.10.5", "0.10.6-SNAPSHOT", "0.11.0")
      ),
      notListable = notListable
    )

  private def complete(c: TestComplete, input: String): Either[Throwable, Seq[String]] =
    c.complete(input, "2.12.20", "2.12").map(_.completions)

  val tests = Tests {

    // The Sonatype snapshot repositories answer "Directory listing forbidden" on their root, while
    // every directory below it can be listed just fine. Completion used to walk all the way up to
    // the root, and so returned nothing at all there - see
    // https://github.com/coursier/coursier/issues/1700
    test("root listing not required") {

      test("org") {
        val c   = newComplete(notListable = Set(root))
        val res = complete(c, "org.scala")
        assert(res == Right(Seq("org.scala-lang", "org.scalameta")))
        assert(!c.listed.contains(root))
      }

      test("name") {
        val c   = newComplete(notListable = Set(root))
        val res = complete(c, "org.scalameta:metals")
        assert(res == Right(Seq("metals_2.12", "metals_2.13")))
        assert(!c.listed.contains(root))
      }

      test("version") {
        val c   = newComplete(notListable = Set(root))
        val res = complete(c, "org.scalameta:metals_2.12:0.10")
        assert(res == Right(Seq("0.10.5", "0.10.6-SNAPSHOT")))
        assert(!c.listed.contains(root))
      }

      test("nested org") {
        val c   = newComplete(notListable = Set(root))
        val res = complete(c, "com.example:thing")
        assert(res == Right(Seq("thing")))
        assert(!c.listed.contains(root))
      }
    }

    test("only list what's needed") {

      test("org") {
        val c = newComplete()
        complete(c, "org.scala")
        assert(c.listed.toList == List(Seq("org")))
      }

      test("name") {
        val c = newComplete()
        complete(c, "org.scalameta:metals")
        // 'org/' to check that org.scalameta is there, then 'org/scalameta/' to complete the name
        assert(c.listed.toList == List(Seq("org"), Seq("org", "scalameta")))
      }

      test("version") {
        val c = newComplete()
        complete(c, "org.scalameta:metals_2.12:0.10")
        // 'org/' to check that org.scalameta is there, 'org/scalameta/' to check that metals_2.12
        // is there, then the versions of metals_2.12 - and that's it
        assert(c.listed.toList == List(Seq("org"), Seq("org", "scalameta")))
      }
    }

    test("completing a top-level org lists the root") {
      val c   = newComplete()
      val res = complete(c, "or")
      assert(res == Right(Seq("org")))
      assert(c.listed.toList == List(root))
    }

    test("unknown org") {

      test("under a known one") {
        val c   = newComplete()
        val res = complete(c, "org.zzz:thing")
        assert(res == Right(Nil))
        // 'org/' has no 'zzz' in it, so we stop there rather than listing 'org/zzz/'
        assert(c.listed.toList == List(Seq("org")))
      }

      test("unknown top-level org") {
        val c   = newComplete()
        val res = complete(c, "zzz.foo:thing")
        assert(res == Right(Nil))
        assert(c.listed.toList == List(Seq("zzz")))
      }

      // We no longer know whether the parent org is there, so the failed listing is now reported
      // rather than silently turned into an empty result
      test("completing an unknown org reports the error") {
        val c   = newComplete()
        val res = complete(c, "zzz.foo")
        assert(res.isLeft)
        assert(c.listed.toList == List(Seq("zzz")))
      }
    }
  }
}
