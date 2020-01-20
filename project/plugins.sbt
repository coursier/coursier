
plugins_(
  "com.eed3si9n"       % "sbt-assembly"             % "0.14.10",
  "com.geirsson"       % "sbt-ci-release"           % "1.5.0",
  "pl.project13.scala" % "sbt-jmh"                  % "0.3.7",
  "org.scalameta"      % "sbt-mdoc"                 % "2.1.1",
  "com.typesafe"       % "sbt-mima-plugin"          % "0.6.1",
  "com.typesafe.sbt"   % "sbt-native-packager"      % "1.6.0",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.12",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.3.0",
  "org.scala-js"       % "sbt-scalajs"              % "0.6.32",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.14.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1",
)

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "2.0.0-RC5-3")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.0-RC5-3")

def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
