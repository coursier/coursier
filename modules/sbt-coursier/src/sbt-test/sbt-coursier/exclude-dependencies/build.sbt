
scalaVersion := "2.11.8"

organization := "io.get-coursier.test"
name := "sbt-coursier-exclude-dependencies"
version := "0.1.0-SNAPSHOT"

libraryDependencies += "com.github.alexarchambault" %% "argonaut-shapeless_6.1" % "1.0.0-RC1"

excludeDependencies += sbt.ExclusionRule("com.chuusai", "shapeless_2.11")
excludeDependencies += "io.argonaut" %% "argonaut"
