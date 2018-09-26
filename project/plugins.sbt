
plugins_(
  "io.get-coursier"    % "sbt-coursier"             % coursierVersion,
  "com.typesafe"       % "sbt-mima-plugin"          % "0.3.0",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.11",
  "com.jsuereth"       % "sbt-pgp"                  % "1.1.1",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.3.0",
  "com.github.gseitz"  % "sbt-release"              % "1.0.8",
  "org.scala-js"       % "sbt-scalajs"              % "0.6.23",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.13.1",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0",
  "io.get-coursier"    % "sbt-shading"              % coursierVersion,
  "org.xerial.sbt"     % "sbt-sonatype"             % "2.3",
  "com.timushev.sbt"   % "sbt-updates"              % "0.3.4",
  "org.tpolecat"       % "tut-plugin"               % "0.6.4"
)

libs ++= Seq(
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value,
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full), // for shapeless / auto type class derivations
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M8"
)

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")


def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
