
plugins_(
  "com.eed3si9n"       % "sbt-assembly"             % "0.14.10",
  "com.geirsson"       % "sbt-ci-release"           % "1.2.6",
  "org.scala-sbt"      % "sbt-contraband"           % "0.4.4",
  "io.get-coursier"    % "sbt-coursier"             % sbtCoursierVersion,
  "pl.project13.scala" % "sbt-jmh"                  % "0.3.7",
  "com.typesafe"       % "sbt-mima-plugin"          % "0.3.0",
  "com.typesafe.sbt"   % "sbt-native-packager"      % "1.3.25",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.12",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.3.0",
  "org.scala-js"       % "sbt-scalajs"              % "0.6.28",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.14.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1",
  "io.get-coursier"    % "sbt-shading"              % sbtCoursierVersion
)

def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
