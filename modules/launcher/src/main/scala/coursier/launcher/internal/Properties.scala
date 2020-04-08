package coursier.launcher.internal

import java.util.{Properties => JProperties}

object Properties {

  private lazy val props = {
    val p = new JProperties()
    try {
      p.load(
        getClass
          .getClassLoader
          .getResourceAsStream("coursier/launcher/coursier.properties")
      )
    }
    catch  {
      case e: NullPointerException =>
    }
    p
  }

  lazy val version = props.getProperty("version")
  lazy val commitHash = props.getProperty("commit-hash")

}
