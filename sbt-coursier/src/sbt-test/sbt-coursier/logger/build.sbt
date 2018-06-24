val logFile = settingKey[File]("")

// Arbitrary dependency with no transitive dependencies
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
// We want to control when the cache gets a hit
coursierCache := baseDirectory.value / "cache"
logFile := baseDirectory.value / "log"

coursierLoggerFactory := {
  val log = new java.io.PrintStream(logFile.value)
  val cacheFile = coursierCache.value
  ;{ () =>
    new coursier.Cache.Logger {
      override def init(beforeOutput: => Unit): Unit = {
        beforeOutput
        log.println("init")
      }
      override def foundLocally(url: String, file: File): Unit = {
        log.println(s"found $url at ${IO.relativize(cacheFile, file).get}")
      }
      override def downloadingArtifact(url: String, file: File): Unit = {
        log.println(s"downloading $url to ${IO.relativize(cacheFile, file).get}")
      }
      override def downloadedArtifact(url: String, success: Boolean): Unit = {
        log.println(s"downloaded $url: $success")
      }
      override def stopDidPrintSomething(): Boolean = {
        log.println("stop")
        true
      }
    }
  }
}

TaskKey[Unit]("checkDownloaded") := {
  val log = IO.readLines(logFile.value)
  if (log.head != "init") {
    sys.error(s"log started with '${log.head}', not init")
  }
  if (log.last != "stop") {
    sys.error(s"log ended with '${log.last}', not stop")
  }
  val url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar"
  val path = "https/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar"
  for (needle <- Seq(s"downloading $url to $path",
                     s"downloaded $url: true")) {
    if (!log.contains(needle)) {
      sys.error(s"log doesn't contain '$needle'")
    }
  }
}

TaskKey[Unit]("checkFound") := {
  val log = IO.readLines(logFile.value)
  if (log.head != "init") {
    sys.error(s"log started with '${log.head}', not init")
  }
  if (log.last != "stop") {
    sys.error(s"log ended with '${log.last}', not stop")
  }
  val url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar"
  val path = "https/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar"
  for (needle <- Seq(s"found $url at $path")) {
    if (!log.contains(needle)) {
      sys.error(s"log doesn't contain '$needle'")
    }
  }
}
