
import $file.Util

import java.nio.file.{Files, Path, StandardCopyOption}

/**
 * Uploads files to a GitHub repository
 *
 * @param generatedFiles List of local files / name in the repository, to upload
 * @param ghOrg GitHub organization of the repository to upload to
 * @param ghProj GitHub project name of the repository to upload to
 * @param branch Branch to upload to
 * @param ghToken GitHub token
 * @param message Commit message
 * @param dryRun Whether to run a dry run (print actions that would have been done, but don't push / upload anything)
 */
def apply(
  generatedFiles: Seq[(Path, String)],
  ghOrg: String,
  ghProj: String,
  branch: String,
  ghToken: String,
  message: String,
  dryRun: Boolean
): Unit =
  Util.withTmpDir(s"$ghOrg-$ghProj-$branch") { tmpDir =>
    val tmpDir0 = tmpDir.toFile

    val repo = s"https://$ghToken@github.com/$ghOrg/$ghProj.git"
    System.err.println(s"Cloning ${repo.replace(ghToken, "****")} in $tmpDir")
    Util.run(Seq("git", "clone", repo, "-q", "-b", branch, tmpDir0.getAbsolutePath))

    Util.run(Seq("git", "config", "user.name", "Travis-CI"), tmpDir0)
    Util.run(Seq("git", "config", "user.email", "invalid@travis-ci.com"), tmpDir0)

    for ((path, name) <- generatedFiles) {
      val dest = tmpDir.resolve(name)
      System.err.println(s"Copying $path to $dest")
      Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
      Util.run(Seq("git", "add", "--", name), tmpDir0)
    }

    val hasChanges = Util.gitRepoHasChanges(tmpDir0)
    if (hasChanges) {
      Util.run(Seq("git", "commit", "-m", message), tmpDir0)

      if (dryRun)
        System.err.println("Dummy mode, not pushing changes")
      else {
        System.err.println("Pushing changes")
        Util.run(Seq("git", "push", "origin", branch), tmpDir0)
      }
    } else
      System.err.println("Nothing changed")
  }
