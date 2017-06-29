scalaVersion := "2.11.8"

libraryDependencies += ("org.jclouds.api" % "nova" % "1.5.9")
  .classifier("tests")

classpathTypes += "test-jars"

resolvers ++= Seq(
  MavenRepository("Maven Central", "http://central.maven.org/maven2/")
)


coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
