
plugins_(
  "io.get-coursier"    % "sbt-coursier"             % coursierVersion,
  "com.typesafe"       % "sbt-mima-plugin"          % "0.3.0",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.11",
  "com.jsuereth"       % "sbt-pgp"                  % "1.1.1",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.3.0",
  "org.scala-js"       % "sbt-scalajs"              % "0.6.25",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.13.1",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.4.0",
  "io.get-coursier"    % "sbt-shading"              % coursierVersion,
  "org.xerial.sbt"     % "sbt-sonatype"             % "2.3",
  "com.timushev.sbt"   % "sbt-updates"              % "0.3.4"
)

libs += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

// required for just released things
resolvers += Resolver.sonatypeRepo("releases")


def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)
def libs = libraryDependencies
