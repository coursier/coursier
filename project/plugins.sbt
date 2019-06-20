
plugins_(
  "com.eed3si9n"       % "sbt-assembly"             % "0.14.9",
  "com.geirsson"       % "sbt-ci-release"           % "1.2.6",
  "org.scala-sbt"      % "sbt-contraband"           % "0.4.3",
  "io.get-coursier"    % "sbt-coursier"             % sbtCoursierVersion,
  "pl.project13.scala" % "sbt-jmh"                  % "0.3.7",
  "com.typesafe"       % "sbt-mima-plugin"          % "0.3.0",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.11",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.3.0",
  "org.scala-js"       % "sbt-scalajs"              % "0.6.28",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.13.1",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0",
  "io.get-coursier"    % "sbt-shading"              % sbtCoursierVersion
)

def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
