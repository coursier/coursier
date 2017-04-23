scalaVersion := "2.11.8"

libraryDependencies += ("org.jclouds.api" % "nova" % "1.5.9")
  .classifier("tests")

libraryDependencies += ("org.jclouds.api" % "nova" % "1.5.9")

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}
