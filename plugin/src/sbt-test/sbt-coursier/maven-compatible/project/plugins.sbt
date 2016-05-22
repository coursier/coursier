{
  val pluginVersion = sys.props.getOrElse(
    "plugin.version",
    throw new RuntimeException(
      """|The system property 'plugin.version' is not defined.
         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
  )

  val isCoursierJava6 = sys.props.contains("coursier.isJava6")
  val sbtCoursierName = if (isCoursierJava6) "sbt-coursier-java-6" else "sbt-coursier"

  addSbtPlugin("io.get-coursier" % sbtCoursierName % pluginVersion)
}
