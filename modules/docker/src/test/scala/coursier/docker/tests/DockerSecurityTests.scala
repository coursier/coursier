package coursier.docker.tests

import coursier.cache.TestUtil._
import coursier.docker.{DockerBuild, DockerUtil}
import coursier.docker.vm.Vm
import utest._

import scala.util.Properties

object DockerSecurityTests extends TestSuite {

  // sha256 of "hello"
  private val helloSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

  val tests = Tests {

    test("vmSshPortBoundToLoopback") {
      val netdev = Vm.netdevOption(2222)
      assert(netdev == "id=net00,type=user,hostfwd=tcp:127.0.0.1:2222-:22")
      // every port forward must be bound to the loopback interface
      val forwards = netdev.split(',').filter(_.startsWith("hostfwd=")).toSeq
      assert(forwards.nonEmpty)
      assert(forwards.forall(_.startsWith("hostfwd=tcp:127.0.0.1:")))
    }

    test("checkDigest") {
      test("valid") {
        withTmpDir { dir =>
          val f = dir / "blob"
          os.write(f, "hello")
          DockerUtil.checkDigest(f.toIO, s"sha256:$helloSha256")
          assert(os.exists(f))
        }
      }
      test("mismatch") {
        withTmpDir { dir =>
          val f = dir / "blob"
          os.write(f, "tampered")
          val ex = assertThrows[Exception] {
            DockerUtil.checkDigest(f.toIO, s"sha256:$helloSha256")
          }
          assert(ex.getMessage.contains("Digest mismatch"))
          assert(!os.exists(f))
        }
      }
      test("malformed") {
        withTmpDir { dir =>
          val f = dir / "blob"
          os.write(f, "hello")
          for (digest <- Seq(helloSha256, s"md5:$helloSha256", "sha256:abc", "sha256:../x"))
            assertThrows[IllegalArgumentException] {
              DockerUtil.checkDigest(f.toIO, digest)
            }
          assert(os.exists(f))
        }
      }
    }

    test("blobDigest") {
      val url = s"https://registry-1.docker.io/v2/library/alpine/blobs/sha256:$helloSha256"
      assert(DockerUtil.blobDigest(url) == Some(s"sha256:$helloSha256"))
      assert(
        DockerUtil.blobDigest("https://registry-1.docker.io/v2/library/alpine/manifests/latest")
          .isEmpty
      )
    }

    test("copySource") {
      def withContext[T](f: (os.Path, String => Either[String, os.Path]) => T): T =
        withTmpDir { dir0 =>
          val dir     = os.Path(dir0.toNIO.toRealPath())
          val context = dir / "context"
          os.write(context / "a" / "file", "a", createFolders = true)
          os.write(dir / "outside" / "secret", "secret", createFolders = true)
          f(dir, DockerBuild.copySource(context, _))
        }

      test("relative") {
        withContext { (dir, resolve) =>
          val context = dir / "context"
          assert(resolve("a/file") == Right(context / "a" / "file"))
          assert(resolve("./a/./file") == Right(context / "a" / "file"))
        }
      }
      test("absolute is relative to context") {
        withContext { (dir, resolve) =>
          val context = dir / "context"
          assert(resolve("/a/file") == Right(context / "a" / "file"))
          assert(resolve("//a//file") == Right(context / "a" / "file"))
          assert(resolve("/../a/file") == Right(context / "a" / "file"))
          assert(resolve("/") == Right(context))
          assert(resolve("/etc/shadow").isLeft) // no etc directory in the context
        }
      }
      test("dot dot can't escape context") {
        withContext { (dir, resolve) =>
          val context = dir / "context"
          assert(resolve("../outside/secret").isLeft)
          assert(resolve("../../a/file") == Right(context / "a" / "file"))
          assert(resolve("a/../../a/file") == Right(context / "a" / "file"))
          assert(resolve("..") == Right(context))
        }
      }
      test("symlinked parent can't escape context") {
        if (!Properties.isWin)
          withContext { (dir, resolve) =>
            os.symlink(dir / "context" / "out", dir / "outside")
            val res = resolve("out/secret")
            assert(res.left.exists(_.contains("outside of the build context")))
          }
      }
      test("symlinked parent within context") {
        if (!Properties.isWin)
          withContext { (dir, resolve) =>
            val context = dir / "context"
            os.symlink(context / "b", context / "a")
            assert(resolve("b/file") == Right(context / "b" / "file"))
          }
      }
      test("symlink as source is copied as is") {
        if (!Properties.isWin)
          withContext { (dir, resolve) =>
            val context = dir / "context"
            os.symlink(context / "link", dir / "outside" / "secret")
            assert(resolve("link") == Right(context / "link"))
          }
      }
    }
  }

}
