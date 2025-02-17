# Gradle Modules support

coursier has some support for [Gradle Modules](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html).

Gradle Module files are `.module` files that are pused alongside `.pom` files to Maven
repositories by some Gradle projects. These contain richer metadata that POM files,
allowing users to specify different dependency sets and artifacts depending on the
platform being used (JVM, Android, native, …), in particular.

For example, [this dependency](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-dom-api-compat/2.1.10/) publishes a `.module` file alongside its POM file. Such files are especially
used in the Android and Kotlin ecosystems.

Coursier is able to read and take into account `.module` files. One has to add
variant attributes to dependencies for coursier to do so. See how that can be done [via the API](api-dependency.md#gradle-module-variant)
and [via the CLI](cli-dependency.md#gradle-module-variant).
