addSbtPlugin("io.get-coursier" % "sbt-coursier" % sbtCoursierVersion0)

def sbtCoursierVersion0 = "1.1.0-M8"

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")
