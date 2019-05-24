package coursier.cli.util

object Guard {
  def apply(): Unit = {
    val experimental =
      sys.env.get("COURSIER_EXPERIMENTAL").exists(s => s == "1" || s == "true") ||
        java.lang.Boolean.getBoolean("coursier.experimental")
    if (!experimental) {
      System.err.println(
        "Command disabled. Set environment variable COURSIER_EXPERIMENTAL=1, " +
          "or Java property coursier.experimental=true."
      )
      sys.exit(1)
    }
  }
}
