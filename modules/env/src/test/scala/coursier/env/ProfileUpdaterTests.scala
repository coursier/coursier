package coursier.env

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Arrays

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.scalatest.FlatSpec

class ProfileUpdaterTests extends FlatSpec {

  it should "update variable in ~/.profile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val update = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = update.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    update.addPath("/foo/bin")

    val expectedInDotProfile =
      """
        |export PATH="/foo/bin:$PATH"
        |""".stripMargin
    val dotProfile = new String(Files.readAllBytes(fs.getPath("/home/alex/.profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))
  }

  it should "set variable in ~/.profile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val update = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = update.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    update.setJavaHome("/foo/jvm/oracle-jdk-1.5")

    val expectedInDotProfile =
      """
        |export JAVA_HOME="/foo/jvm/oracle-jdk-1.5"
        |""".stripMargin
    val dotProfile = new String(Files.readAllBytes(fs.getPath("/home/alex/.profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))
  }

  it should "create ~/.profile and ~/.zprofile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val env = Map("SHELL" -> "zsh")
    val update = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(env.get))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Set(
      "/home/alex/.profile",
      "/home/alex/.zprofile"
    )
    val profileFiles = update.profileFiles().map(_.toString).toSet
    assert(profileFiles == expectedProfileFiles)

    update.addPath("/foo/bin")

    val expectedInDotProfileFiles =
      """
        |export PATH="/foo/bin:$PATH"
        |""".stripMargin

    val dotProfile = new String(Files.readAllBytes(fs.getPath("/home/alex/.profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfileFiles))

    val dotZprofile = new String(Files.readAllBytes(fs.getPath("/home/alex/.zprofile")), StandardCharsets.UTF_8)
    assert(dotZprofile.contains(expectedInDotProfileFiles))
  }

  it should "create ~/.profile and ~/.zprofile and update ~/.bash_profile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())

    val bashProfilePath = fs.getPath("/home/alex/.bash_profile")
    Files.createDirectories(bashProfilePath.getParent)
    Files.write(bashProfilePath, Array.emptyByteArray)

    val home = fs.getPath("/home/alex")
    val env = Map("SHELL" -> "zsh")
    val update = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(env.get))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Set(
      "/home/alex/.profile",
      "/home/alex/.zprofile",
      "/home/alex/.bash_profile"
    )
    val profileFiles = update.profileFiles().map(_.toString).toSet
    assert(profileFiles == expectedProfileFiles)

    update.addPath("/foo/bin")

    val expectedInDotProfileFiles =
      """
        |export PATH="/foo/bin:$PATH"
        |""".stripMargin

    val dotProfile = new String(Files.readAllBytes(fs.getPath("/home/alex/.profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfileFiles))

    val dotZprofile = new String(Files.readAllBytes(fs.getPath("/home/alex/.zprofile")), StandardCharsets.UTF_8)
    assert(dotZprofile.contains(expectedInDotProfileFiles))

    val dotBashProfile = new String(Files.readAllBytes(bashProfilePath), StandardCharsets.UTF_8)
    assert(dotBashProfile.contains(expectedInDotProfileFiles))
  }

  it should "take ZDOTDIR into account" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val env = Map("SHELL" -> "zsh", "ZDOTDIR" -> "/the/zdotdir")
    val update = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(env.get))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Set(
      "/home/alex/.profile",
      "/the/zdotdir/.zprofile"
    )
    val profileFiles = update.profileFiles().map(_.toString).toSet
    assert(profileFiles == expectedProfileFiles)

    update.addPath("/foo/bin")

    val expectedInDotProfileFiles =
      """
        |export PATH="/foo/bin:$PATH"
        |""".stripMargin

    val dotProfile = new String(Files.readAllBytes(fs.getPath("/home/alex/.profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfileFiles))

    val dotZprofile = new String(Files.readAllBytes(fs.getPath("/the/zdotdir/.zprofile")), StandardCharsets.UTF_8)
    assert(dotZprofile.contains(expectedInDotProfileFiles))
  }

  it should "be idempotent" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val update = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = update.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    update.addPath("/foo/bin")

    val expectedInDotProfile =
      """
        |export PATH="/foo/bin:$PATH"
        |""".stripMargin
    val dotProfileBytes = Files.readAllBytes(fs.getPath("/home/alex/.profile"))
    val dotProfile = new String(dotProfileBytes, StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))

    // update a second time, that shouldn't change ~/.profile this time
    update.addPath("/foo/bin")

    val newDotProfileBytes = Files.readAllBytes(fs.getPath("/home/alex/.profile"))
    assert(Arrays.equals(dotProfileBytes, newDotProfileBytes))
  }

}
