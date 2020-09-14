
plugins_(
  "com.geirsson"       % "sbt-ci-release"           % "1.5.3",
  "pl.project13.scala" % "sbt-jmh"                  % "0.4.0",
  "org.scalameta"      % "sbt-mdoc"                 % "2.2.1",
  "org.xerial.sbt"     % "sbt-pack"                 % "0.12",
  "com.lightbend.sbt"  % "sbt-proguard"             % "0.4.0",
  "org.scala-js"       % "sbt-scalajs"              % "1.0.1",
  "ch.epfl.scala"      % "sbt-scalajs-bundler"      % "0.17.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
)

addSbtPlugin("io.github.alexarchambault.sbt" % "sbt-compatibility" % "0.0.7")
addSbtPlugin("io.github.alexarchambault.sbt" % "sbt-eviction-rules" % "0.2.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.0")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.0.0")

def plugins_(modules: ModuleID*) = modules.map(addSbtPlugin)

resolvers += Resolver.sonatypeRepo("snapshots")
