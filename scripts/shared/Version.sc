
/**
 * Latest version according to git tags
 */
def latestFromTag: String = {
  import sys.process._
  val cmd = Seq("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
  cmd.!!.trim.stripPrefix("v")
}

/**
 * Latest version according to environment variable `TRAVIS_TAG`
 */
def latestFromTravisTag: String = {
  val tag = sys.env.getOrElse(
    "TRAVIS_TAG",
    sys.error("TRAVIS_TAG not set")
  )
  if (tag.startsWith("v"))
    tag.stripPrefix("v")
  else
    sys.error(s"TRAVIS_TAG ('$tag') doesn't start with 'v'")
}
