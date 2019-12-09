
plugins_(
  "com.eed3si9n"       % "sbt-assembly"             % "0.14.10",
  "com.geirsson"       % "sbt-ci-release"           % "1.4.31",
  "pl.project13.scala" % "sbt-jmh"                  % "0.3.7",
  "com.typesafe"       % "sbt-mima-plugin"          % "0.6.1",
  "com.typesafe.sbt"   % "sbt-native-packager"      % "1.5.1",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.12",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.3.0",
  "org.scala-js"       % "sbt-scalajs"              % "0.6.31",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.16.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1",
)

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "2.0.0-RC5-2")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.0-RC5-2")

def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
