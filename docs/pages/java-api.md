# coursier Java API

coursier can be used from pure Java projects, but also from Scala projects, via its Java API.

The coursier Java API is currently handled from [an external repository](https://github.com/coursier/interface), although it might be moved to the main repository at some point.

It is published as `io.get-coursier:interface`, and aims at minimizing binary compatibility
breakages - it basically never broke binary compatibility since its very first release (but
for one borked release).

It only depends on `slf4j-api`. Most notably, it doesn't depend on the Scala standard library
or any other Scala project. coursier being written in Scala, this is achieved by shading / proguarding
all coursier dependencies. This results in a slightly heavy JAR, that embeds under the
`coursierapi.shaded` most coursier dependencies. Note that users shouldn't tap directly into
the APIs under `coursierapi.shaded` - these are considered private, and no compatibility guarantees
apply to those.
