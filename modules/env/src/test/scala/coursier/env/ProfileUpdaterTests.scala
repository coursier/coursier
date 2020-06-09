package coursier.env

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Arrays

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.scalatest.flatspec.AnyFlatSpec

class ProfileUpdaterTests extends AnyFlatSpec {

  private def indicesOf(string: String, subString: String): Seq[Int] = {

    def helper(fromIdx: Int): Stream[Int] = {
      val newIdx = string.indexOf(subString, fromIdx)
      if (newIdx >= 0)
        newIdx #:: helper(newIdx + 1)
      else
        Stream.empty
    }

    helper(0).toVector
  }

  it should "update variable in ~/.profile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")

    val initialContent =
      """# hello
        |export A="a"
        |# foo
        |
        |""".stripMargin
    ProfileUpdater.createDirectories(home)
    Files.write(home.resolve(".profile"), initialContent.getBytes("UTF-8"))

    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = updater.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update)

    val expectedInDotProfile =
      """
        |export PATH="$PATH:/foo/bin"
        |""".stripMargin
    val dotProfile = new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))

    assert(dotProfile.contains(initialContent))
  }

  it should "set variable in ~/.profile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = updater.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate().withSet(Seq("JAVA_HOME" -> "/foo/jvm/oracle-jdk-1.5"))
    updater.applyUpdate(update)

    val expectedInDotProfile =
      """
        |export JAVA_HOME="/foo/jvm/oracle-jdk-1.5"
        |""".stripMargin
    val dotProfile = new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))
  }

  it should "create ~/.profile and ~/.zprofile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val env = Map("SHELL" -> "/bin/zsh")
    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(env.get))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Set(
      "/home/alex/.profile",
      "/home/alex/.zprofile"
    )
    val profileFiles = updater.profileFiles().map(_.toString).toSet
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update)

    val expectedInDotProfileFiles =
      """
        |export PATH="$PATH:/foo/bin"
        |""".stripMargin

    val dotProfile = new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfileFiles))

    val dotZprofile = new String(Files.readAllBytes(fs.getPath("/home/alex/.zprofile")), StandardCharsets.UTF_8)
    assert(dotZprofile.contains(expectedInDotProfileFiles))
  }

  it should "create ~/.profile and ~/.zprofile and update ~/.bash_profile" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())

    val bashProfilePath = fs.getPath("/home/alex/.bash_profile")
    ProfileUpdater.createDirectories(bashProfilePath.getParent)
    Files.write(bashProfilePath, Array.emptyByteArray)

    val home = fs.getPath("/home/alex")
    val env = Map("SHELL" -> "/bin/zsh")
    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(env.get))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Set(
      "/home/alex/.profile",
      "/home/alex/.zprofile",
      "/home/alex/.bash_profile"
    )
    val profileFiles = updater.profileFiles().map(_.toString).toSet
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update)

    val expectedInDotProfileFiles =
      """
        |export PATH="$PATH:/foo/bin"
        |""".stripMargin

    val dotProfile = new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfileFiles))

    val dotZprofile = new String(Files.readAllBytes(fs.getPath("/home/alex/.zprofile")), StandardCharsets.UTF_8)
    assert(dotZprofile.contains(expectedInDotProfileFiles))

    val dotBashProfile = new String(Files.readAllBytes(bashProfilePath), StandardCharsets.UTF_8)
    assert(dotBashProfile.contains(expectedInDotProfileFiles))
  }

  it should "take ZDOTDIR into account" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val env = Map("SHELL" -> "/bin/zsh", "ZDOTDIR" -> "/the/zdotdir")
    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(env.get))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Set(
      "/home/alex/.profile",
      "/the/zdotdir/.zprofile"
    )
    val profileFiles = updater.profileFiles().map(_.toString).toSet
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update)

    val expectedInDotProfileFiles =
      """
        |export PATH="$PATH:/foo/bin"
        |""".stripMargin

    val dotProfile = new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfileFiles))

    val dotZprofile = new String(Files.readAllBytes(fs.getPath("/the/zdotdir/.zprofile")), StandardCharsets.UTF_8)
    assert(dotZprofile.contains(expectedInDotProfileFiles))
  }

  it should "be idempotent" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = updater.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update)

    val expectedInDotProfile =
      """
        |export PATH="$PATH:/foo/bin"
        |""".stripMargin
    val dotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
    val dotProfile = new String(dotProfileBytes, StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))

    // update a second time, that shouldn't change ~/.profile this time
    updater.applyUpdate(update)

    val newDotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
    assert(Arrays.equals(dotProfileBytes, newDotProfileBytes))
  }

  it should "update the previous section" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")
    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")
    val title = "foo title"

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = updater.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update, title)

    val expectedInDotProfile =
      s"""# >>> $title >>>
         |export PATH="$$PATH:/foo/bin"
         |# <<< $title <<<
         |""".stripMargin
    val dotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
    val dotProfile = new String(dotProfileBytes, StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))

    val newUpdate = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/other/bin"))

    updater.applyUpdate(newUpdate, title)

    val newlyExpectedInDotProfile =
      s"""# >>> $title >>>
         |export PATH="$$PATH:/other/bin"
         |# <<< $title <<<
         |""".stripMargin

    val newDotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
    val newDotProfile = new String(newDotProfileBytes, StandardCharsets.UTF_8)
    assert(newDotProfile.contains(newlyExpectedInDotProfile))

    // checking that the last update changed the previous one,
    // rather than simply appended stuff to the file
    val startTagIndices = indicesOf(newDotProfile, s"# >>> $title >>>")
    assert(startTagIndices.length == 1)

    val endTagIndices = indicesOf(newDotProfile, s"# <<< $title <<<")
    assert(endTagIndices.length == 1)

    val exportPathIndices = indicesOf(newDotProfile, "export PATH=")
    assert(endTagIndices.length == 1)
  }

  it should "leave previous content intact" in {
    val fs = Jimfs.newFileSystem(Configuration.unix())
    val home = fs.getPath("/home/alex")

    val initialContent =
      """# hello
        |export A="a"
        |# foo
        |
        |""".stripMargin
    ProfileUpdater.createDirectories(home)
    Files.write(home.resolve(".profile"), initialContent.getBytes("UTF-8"))

    val updater = ProfileUpdater()
      .withHome(Some(home))
      .withGetEnv(Some(_ => None))
      .withCharset(StandardCharsets.UTF_8)
      .withPathSeparator(":")
    val title = "foo title"

    val expectedProfileFiles = Seq("/home/alex/.profile")
    val profileFiles = updater.profileFiles().map(_.toString)
    assert(profileFiles == expectedProfileFiles)

    val update = EnvironmentUpdate()
      .withPathLikeAppends(Seq("PATH" -> "/foo/bin"))

    updater.applyUpdate(update, title)

    val expectedInDotProfile =
      s"""# >>> $title >>>
         |export PATH="$$PATH:/foo/bin"
         |# <<< $title <<<
         |""".stripMargin
    val dotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
    val dotProfile = new String(dotProfileBytes, StandardCharsets.UTF_8)
    assert(dotProfile.contains(expectedInDotProfile))

    updater.tryRevertUpdate(title)

    val newDotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
    val newDotProfile = new String(newDotProfileBytes, StandardCharsets.UTF_8)

    assert(newDotProfile == initialContent)
  }

}
