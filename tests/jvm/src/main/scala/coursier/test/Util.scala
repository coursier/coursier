package coursier.test

import java.io.File
import java.nio.file.Files

import coursier._
import coursier.internal.FileUtil

object Util {

  def check(extraRepo: Repository): Unit = {

    val tmpDir = FileUtil.createTempDirectory("coursier-cache-fetch-tests")

    def cleanTmpDir() = {
      def delete(f: File): Boolean =
        if (f.isDirectory) {
          val removedContent = Option(f.listFiles()).toSeq.flatten.map(delete).forall(x => x)
          val removedDir = f.delete()

          removedContent && removedDir
        } else
          f.delete()

      if (!delete(tmpDir))
        Console.err.println(s"Warning: unable to remove temporary directory $tmpDir")
    }

    val res = try {
      val fetch = Fetch.from(
        Seq(
          extraRepo,
          MavenRepository("https://repo1.maven.org/maven2")
        ),
        Cache.fetch(
          tmpDir
        )
      )

      val startRes = Resolution(
        Set(
          Dependency(
            Module("com.github.alexarchambault", "coursier_2.11"), "1.0.0-M9-test"
          )
        )
      )

      startRes.process.run(fetch).unsafePerformSync
    } finally {
      cleanTmpDir()
    }

    val errors = res.metadataErrors

    assert(errors.isEmpty)
  }

}
