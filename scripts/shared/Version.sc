
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
def latestFromEnv: String =
  latestFromTravisTagOpt
    .orElse(latestFromGitHubRefOpt)
    .getOrElse {
      sys.error("TRAVIS_TAG or GITHUB_REF not set")
    }

/**
 * Latest version according to environment variable `TRAVIS_TAG` if it is set
 */
def latestFromTravisTagOpt: Option[String] = {
  val tagOpt = sys.env.get("TRAVIS_TAG").filter(_.nonEmpty)
  tagOpt.map { tag =>
    if (tag.startsWith("v"))
      tag.stripPrefix("v")
    else
      sys.error(s"TRAVIS_TAG ('$tag') doesn't start with 'v'")
  }
}

def latestFromGitHubRefOpt: Option[String] = {
  val tagOpt = sys.env.get("GITHUB_REF").filter(_.nonEmpty)
  tagOpt.map { tag =>
    if (tag.startsWith("refs/tags/v"))
      tag.stripPrefix("refs/tags/v")
    else
      sys.error(s"GITHUB_REF ('$tag') doesn't start with 'refs/tags/v'")
  }
}
