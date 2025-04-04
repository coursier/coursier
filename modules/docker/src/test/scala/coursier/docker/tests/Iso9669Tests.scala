package coursier.docker.tests

import coursier.docker.vm.iso.Image
import utest._

import java.nio.charset.StandardCharsets

import scala.collection.immutable.ArraySeq
import scala.util.{Properties, Try}

object Iso9669Tests extends TestSuite {

  def tests =
    if (Properties.isLinux)
      actualTests
    else
      Tests {}

  def actualTests = Tests {
    test("mount") {
      val metaDataContent =
        """#cloud-config
          |users:
          |  - name: alex
          |    lock_passwd: false
          |    sudo: ["ALL=(ALL) NOPASSWD:ALL"]
          |    shell: /bin/bash
          |    ssh-authorized-keys:
          |      - ssh-rsa thing-foo
          |""".stripMargin
      val userDataContent =
        """instance-id: someid/somehostname
          |""".stripMargin
      val content = Seq(
        (os.sub / "meta-data") ->
          ArraySeq.unsafeWrapArray(metaDataContent.getBytes(StandardCharsets.UTF_8)),
        (os.sub / "user-data") ->
          ArraySeq.unsafeWrapArray(userDataContent.getBytes(StandardCharsets.UTF_8))
      )
      val image = Image.generate(
        "foo",
        content.map {
          case (path, bytes) =>
            (path.toString, bytes.unsafeArray.asInstanceOf[Array[Byte]])
        }
      )
      val tmpDir    = os.temp.dir(prefix = "cs-iso-tests-", perms = "rwx------")
      val imagePath = tmpDir / "image.iso"
      os.write(imagePath, image, perms = "rw-------")
      val mountDir = tmpDir / "mount"
      os.makeDir(mountDir)
      os.proc("sudo", "mount", "-o", "ro", "-t", "iso9660", imagePath, mountDir)
        .call(stdin = os.Inherit, stdout = os.Inherit)
      try {
        val readContent = os.walk(mountDir)
          .map(_.relativeTo(mountDir).asSubPath)
          .map { path =>
            path -> ArraySeq.unsafeWrapArray(os.read.bytes(mountDir / path))
          }
          .sortBy(_._1)
        if (content != readContent) {
          val content0 = content.map {
            case (path, bytes) =>
              (
                path,
                Try(new String(bytes.unsafeArray.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8))
              )
          }
          val readContent0 = readContent.map {
            case (path, bytes) =>
              (
                path,
                Try(new String(bytes.unsafeArray.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8))
              )
          }
          pprint.err.log(content0)
          pprint.err.log(readContent0)
        }
        assert(content == readContent)
      }
      finally
        os.proc("sudo", "umount", mountDir)
          .call(stdin = os.Inherit, stdout = os.Inherit)
    }
  }

}
