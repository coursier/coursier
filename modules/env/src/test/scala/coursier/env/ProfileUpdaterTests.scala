package coursier.env

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Arrays

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import utest._

import scala.collection.compat.immutable.LazyList

object ProfileUpdaterTests extends TestSuite {

  private def indicesOf(string: String, subString: String): Seq[Int] = {

    def helper(fromIdx: Int): LazyList[Int] = {
      val newIdx = string.indexOf(subString, fromIdx)
      if (newIdx >= 0)
        newIdx #:: helper(newIdx + 1)
      else
        LazyList.empty
    }

    helper(0).toVector
  }

  val tests = Tests {
    test("update variable in ~/.profile") {
      val fs   = Jimfs.newFileSystem(Configuration.unix())
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
        .copy(
          home = Some(home),
          getEnv = Some(_ => None),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Seq("/home/alex/.profile")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update)

      val expectedInDotProfile =
        """
          |export PATH="$PATH:/foo/bin"
          |""".stripMargin
      val dotProfile =
        new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfile))

      assert(dotProfile.contains(initialContent))
    }

    test("set variable in ~/.profile") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(_ => None),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Seq("/home/alex/.profile")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate().copy(set = Seq("JAVA_HOME" -> "/foo/jvm/oracle-jdk-1.5"))
      updater.applyUpdate(update)

      val expectedInDotProfile =
        """
          |export JAVA_HOME="/foo/jvm/oracle-jdk-1.5"
          |""".stripMargin
      val dotProfile =
        new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfile))
    }

    test("set variable in ~/.config/alex/fish/config.fish") {
      val fs   = Jimfs.newFileSystem(Configuration.unix())
      val home = fs.getPath("/home/alex/")
      val env  = Map(
        "SHELL" -> "/bin/fish"
      )
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(env.get),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Seq("/home/alex/.config/fish/config.fish")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate().copy(set = Seq("JAVA_HOME" -> "/foo/jvm/oracle-jdk-1.5"))
      updater.applyUpdate(update)

      val expectedInFishConfig =
        """
          |set -gx JAVA_HOME "/foo/jvm/oracle-jdk-1.5"
          |""".stripMargin
      val fishConfig =
        new String(
          Files.readAllBytes(home.resolve(".config/fish/config.fish")),
          StandardCharsets.UTF_8
        )
      assert(fishConfig.contains(expectedInFishConfig))
    }

    test("create ~/.profile and ~/.zprofile") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val env     = Map("SHELL" -> "/bin/zsh")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(env.get),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Set(
        "/home/alex/.profile",
        "/home/alex/.zprofile"
      )
      val profileFiles = updater.profileFiles().map(_.toString).toSet
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update)

      val expectedInDotProfileFiles =
        """
          |export PATH="$PATH:/foo/bin"
          |""".stripMargin

      val dotProfile =
        new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfileFiles))

      val dotZprofile =
        new String(Files.readAllBytes(fs.getPath("/home/alex/.zprofile")), StandardCharsets.UTF_8)
      assert(dotZprofile.contains(expectedInDotProfileFiles))
    }

    test("create ~/.profile and ~/.zprofile and update ~/.bash_profile") {
      val fs = Jimfs.newFileSystem(Configuration.unix())

      val bashProfilePath = fs.getPath("/home/alex/.bash_profile")
      ProfileUpdater.createDirectories(bashProfilePath.getParent)
      Files.write(bashProfilePath, Array.emptyByteArray)

      val home    = fs.getPath("/home/alex")
      val env     = Map("SHELL" -> "/bin/zsh")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(env.get),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Set(
        "/home/alex/.profile",
        "/home/alex/.zprofile",
        "/home/alex/.bash_profile"
      )
      val profileFiles = updater.profileFiles().map(_.toString).toSet
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update)

      val expectedInDotProfileFiles =
        """
          |export PATH="$PATH:/foo/bin"
          |""".stripMargin

      val dotProfile =
        new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfileFiles))

      val dotZprofile =
        new String(Files.readAllBytes(fs.getPath("/home/alex/.zprofile")), StandardCharsets.UTF_8)
      assert(dotZprofile.contains(expectedInDotProfileFiles))

      val dotBashProfile = new String(Files.readAllBytes(bashProfilePath), StandardCharsets.UTF_8)
      assert(dotBashProfile.contains(expectedInDotProfileFiles))
    }

    test("take ZDOTDIR into account") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val env     = Map("SHELL" -> "/bin/zsh", "ZDOTDIR" -> "/the/zdotdir")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(env.get),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Set(
        "/home/alex/.profile",
        "/the/zdotdir/.zprofile"
      )
      val profileFiles = updater.profileFiles().map(_.toString).toSet
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update)

      val expectedInDotProfileFiles =
        """
          |export PATH="$PATH:/foo/bin"
          |""".stripMargin

      val dotProfile =
        new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfileFiles))

      val dotZprofile =
        new String(Files.readAllBytes(fs.getPath("/the/zdotdir/.zprofile")), StandardCharsets.UTF_8)
      assert(dotZprofile.contains(expectedInDotProfileFiles))
    }

    test("be idempotent") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(_ => None),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val expectedProfileFiles = Seq("/home/alex/.profile")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update)

      val expectedInDotProfile =
        """
          |export PATH="$PATH:/foo/bin"
          |""".stripMargin
      val dotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
      val dotProfile      = new String(dotProfileBytes, StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfile))

      // update a second time, that shouldn't change ~/.profile this time
      updater.applyUpdate(update)

      val newDotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
      assert(Arrays.equals(dotProfileBytes, newDotProfileBytes))
    }

    test("update the previous section") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(_ => None),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )
      val title = "foo title"

      val expectedProfileFiles = Seq("/home/alex/.profile")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update, title)

      val expectedInDotProfile =
        s"""# >>> $title >>>
           |export PATH="$$PATH:/foo/bin"
           |# <<< $title <<<
           |""".stripMargin
      val dotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
      val dotProfile      = new String(dotProfileBytes, StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfile))

      val newUpdate = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/other/bin"))

      updater.applyUpdate(newUpdate, title)

      val newlyExpectedInDotProfile =
        s"""# >>> $title >>>
           |export PATH="$$PATH:/other/bin"
           |# <<< $title <<<
           |""".stripMargin

      val newDotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
      val newDotProfile      = new String(newDotProfileBytes, StandardCharsets.UTF_8)
      assert(newDotProfile.contains(newlyExpectedInDotProfile))

      // checking that the last update changed the previous one,
      // rather than simply appended stuff to the file
      val startTagIndices = indicesOf(newDotProfile, s"# >>> $title >>>")
      assert(startTagIndices.length == 1)

      val endTagIndices = indicesOf(newDotProfile, s"# <<< $title <<<")
      assert(endTagIndices.length == 1)

      val exportPathIndices = indicesOf(newDotProfile, "export PATH=")
      assert(exportPathIndices.length == 1)
    }

    test("update the previous section fish") {
      val fs   = Jimfs.newFileSystem(Configuration.unix())
      val home = fs.getPath("/home/alex")
      val env  = Map(
        "SHELL" -> "/bin/fish"
      )
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(env.get),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )
      val title = "foo title"

      val expectedProfileFiles = Seq("/home/alex/.config/fish/config.fish")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update, title)

      val expectedInFishConfig =
        s"""# >>> $title >>>
           |set -gx PATH "$$PATH:/foo/bin"
           |# <<< $title <<<
           |""".stripMargin
      val fishConfigBytes = Files.readAllBytes(home.resolve(".config/fish/config.fish"))
      val fishConfig      = new String(fishConfigBytes, StandardCharsets.UTF_8)
      assert(fishConfig.contains(expectedInFishConfig))

      val newUpdate = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/other/bin"))

      updater.applyUpdate(newUpdate, title)

      val newlyExpectedInDotProfile =
        s"""# >>> $title >>>
           |set -gx PATH "$$PATH:/other/bin"
           |# <<< $title <<<
           |""".stripMargin

      val newDotProfileBytes = Files.readAllBytes(home.resolve(".config/fish/config.fish"))
      val newDotProfile      = new String(newDotProfileBytes, StandardCharsets.UTF_8)
      assert(newDotProfile.contains(newlyExpectedInDotProfile))

      // checking that the last update changed the previous one,
      // rather than simply appended stuff to the file
      val startTagIndices = indicesOf(newDotProfile, s"# >>> $title >>>")
      assert(startTagIndices.length == 1)

      val endTagIndices = indicesOf(newDotProfile, s"# <<< $title <<<")
      assert(endTagIndices.length == 1)

      val exportPathIndices = indicesOf(newDotProfile, "set -gx PATH ")
      assert(exportPathIndices.length == 1)
    }

    test("escape special characters in ~/.profile") {
      for (value <- ShellEval.nastyValues) {
        val fs      = Jimfs.newFileSystem(Configuration.unix())
        val home    = fs.getPath("/home/alex")
        val updater = ProfileUpdater()
          .copy(
            home = Some(home),
            getEnv = Some(_ => None),
            charset = StandardCharsets.UTF_8,
            pathSeparator = ":"
          )

        val update = EnvironmentUpdate().copy(
          set = Seq("JAVA_HOME" -> value),
          pathLikeAppends = Seq("PATH" -> s"$value/bin")
        )
        updater.applyUpdate(update, "coursier")

        val dotProfile =
          new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)

        for (shell <- ShellEval.shells) {
          val (javaHome, createdJavaHome) = ShellEval.eval(shell, dotProfile, "JAVA_HOME")
          assert(javaHome == value)
          assert(createdJavaHome.isEmpty)
          val (path, createdPath) =
            ShellEval.eval(shell, dotProfile, "PATH", Map("PATH" -> "/usr/bin:/bin"))
          assert(path == s"/usr/bin:/bin:$value/bin")
          assert(createdPath.isEmpty)
        }

        // reverting needs to find the escaped content
        assert(updater.tryRevertUpdate("coursier"))
        val newDotProfile =
          new String(Files.readAllBytes(home.resolve(".profile")), StandardCharsets.UTF_8)
        assert(newDotProfile.isEmpty)
      }
    }

    test("escape special characters in fish config") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(Map("SHELL" -> "/bin/fish").get),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val update = EnvironmentUpdate().copy(
        set = Seq("JAVA_HOME" -> "/a\\b\"c$(d)`e`"),
        pathLikeAppends = Seq("PATH" -> "/a$(b)/bin")
      )
      updater.applyUpdate(update)

      val expectedInFishConfig =
        """
          |set -gx JAVA_HOME "/a\\b\"c\$(d)`e`"
          |set -gx PATH "$PATH:/a\$(b)/bin"
          |""".stripMargin
      val fishConfig =
        new String(
          Files.readAllBytes(home.resolve(".config/fish/config.fish")),
          StandardCharsets.UTF_8
        )
      assert(fishConfig.contains(expectedInFishConfig))
    }

    test("reject new lines") {
      val fs      = Jimfs.newFileSystem(Configuration.unix())
      val home    = fs.getPath("/home/alex")
      val updater = ProfileUpdater()
        .copy(
          home = Some(home),
          getEnv = Some(_ => None),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )

      val update = EnvironmentUpdate().copy(set = Seq("JAVA_HOME" -> "/jvm/jdk\ntouch pwned"))
      assertThrows[IllegalArgumentException](updater.applyUpdate(update))
      assert(!Files.exists(home.resolve(".profile")))
    }

    test("leave previous content intact") {
      val fs   = Jimfs.newFileSystem(Configuration.unix())
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
        .copy(
          home = Some(home),
          getEnv = Some(_ => None),
          charset = StandardCharsets.UTF_8,
          pathSeparator = ":"
        )
      val title = "foo title"

      val expectedProfileFiles = Seq("/home/alex/.profile")
      val profileFiles         = updater.profileFiles().map(_.toString)
      assert(profileFiles == expectedProfileFiles)

      val update = EnvironmentUpdate()
        .copy(pathLikeAppends = Seq("PATH" -> "/foo/bin"))

      updater.applyUpdate(update, title)

      val expectedInDotProfile =
        s"""# >>> $title >>>
           |export PATH="$$PATH:/foo/bin"
           |# <<< $title <<<
           |""".stripMargin
      val dotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
      val dotProfile      = new String(dotProfileBytes, StandardCharsets.UTF_8)
      assert(dotProfile.contains(expectedInDotProfile))

      updater.tryRevertUpdate(title)

      val newDotProfileBytes = Files.readAllBytes(home.resolve(".profile"))
      val newDotProfile      = new String(newDotProfileBytes, StandardCharsets.UTF_8)

      assert(newDotProfile == initialContent)
    }
  }
}
