# Gradle Modules support

coursier has some support for [Gradle Modules](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html). Gradle Modules allow resolutions to be more precise,
fetching artifacts and dependencies more adapted to your environment. These are especially
used in the Kotlin and Android ecosystems.

Gradle Modules are `.module` files, like [this one](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-dom-api-compat/2.1.10/), that are pushed alongside `.pom` files to Maven
repositories by some Gradle projects. They contain richer metadata that POM files,
allowing users to specify different dependency sets and artifacts depending on the
platform being used (JVM, Android, native, …), in particular.

Coursier is able to read and take into account `.module` files. Support for Gradle Modules needs
to be explicitly enabled for now. See how that can be done [via the API](api-dependency.md#gradle-module-variant)
and [via the CLI](cli-dependency.md#gradle-module-variant).

## Attributes matching

When fetching dependencies with Gradle Module support enabled, you should pass variant attributes
to coursier. These attributes specify details about the environment you want to fetch dependencies for.

For example, from the command-line, resolving some dependencies with Gradle Module support enabled can fail at first if we don't pass variant attributes:
```text
$ cs resolve org.jetbrains.kotlinx:kotlinx-html-js:0.11.0 --enable-modules
Resolution error: Error downloading org.jetbrains.kotlin:kotlin-stdlib:1.9.22
  Found too many variants in org.jetbrains.kotlin:kotlin-stdlib:1.9.22 for { org.gradle.category=library, org.gradle.usage=runtime }:
jsRuntimeElements: {
  org.gradle.category: library
  org.gradle.jvm.environment: non-jvm
  org.gradle.usage: kotlin-runtime
  org.jetbrains.kotlin.js.compiler: ir
  org.jetbrains.kotlin.platform.type: js
  $relocated: true
}
jsV1RuntimeElements: {
  org.gradle.category: library
  org.gradle.jvm.environment: non-jvm
  org.gradle.usage: kotlin-runtime
  org.jetbrains.kotlin.js.compiler: legacy
  org.jetbrains.kotlin.platform.type: js
  $relocated: true
}
jvmRuntimeElements: {
  org.gradle.category: library
  org.gradle.jvm.environment: standard-jvm
  org.gradle.libraryelements: jar
  org.gradle.usage: java-runtime
  org.jetbrains.kotlin.platform.type: jvm
}
wasmJsRuntimeElements: {
  org.gradle.category: library
  org.gradle.jvm.environment: non-jvm
  org.gradle.usage: kotlin-runtime
  org.jetbrains.kotlin.platform.type: wasm
  org.jetbrains.kotlin.wasm.target: js
  $relocated: true
}
wasmWasiRuntimeElements: {
  org.gradle.category: library
  org.gradle.jvm.environment: non-jvm
  org.gradle.usage: kotlin-runtime
  org.jetbrains.kotlin.platform.type: wasm
  org.jetbrains.kotlin.wasm.target: wasi
  $relocated: true
}
```

The error implies that coursier wasn't able to pick a variant among the ones it found, that are displayed.
In order to address that, we must pass variant attributes, that are going to be matched against those of the
variants proposed by each module.

Passing variant attributes resolves that issue:
```text
$ cs resolve org.jetbrains.kotlinx:kotlinx-html-js:0.11.0 --enable-modules --variant org.gradle.category=library --variant org.gradle.jvm.environment=standard-jvm
org.jetbrains:annotations:13.0:default
org.jetbrains.kotlin:kotlin-dom-api-compat:1.9.22:{ org.gradle.category=library, org.gradle.jvm.environment=standard-jvm, org.gradle.usage=runtime }
org.jetbrains.kotlin:kotlin-stdlib:1.9.22:{ org.gradle.category=library, org.gradle.jvm.environment=standard-jvm, org.gradle.usage=runtime }
org.jetbrains.kotlinx:kotlinx-html-js:0.11.0:{ org.gradle.category=library, org.gradle.jvm.environment=standard-jvm, org.gradle.usage=runtime }
```

In order to pick the variant of a module, coursier looks at the variant attributes it is passed as input
(in the command above, `org.gradle.category` with value `library`, and `org.gradle.jvm.environment` with value
`standard-jvm`). coursier iterates on each of those input attributes, and a variant is retained if
and only if:

* it either doesn't have that attribute, or
* it has that attribute, and its value is the same as the one in the user input (matching of the values is actually more refined, see below)

For a variant to be retained, either of those conditions have to be met for all input attributes.

If only a single variant remains after that selection, it is picked. If none remain, resolution fails.
If several variants remain, coursier keeps the one(s) that match(es) the most input values (second condition above
met for the most input attributes); if that still corresponds to several variants, then resolution fails.

## Attribute value comparisons

Most of the time, values of input versus incoming module attributes are directly compared, and match
if they're equal. For some attributes, the matching can be more loose:

* for `org.gradle.usage`, if users pass `api` as a value, then things like `java-api` or `kotlin-api`
also match - more generally, anything ending in `-api` is accepted, alongside just `api`
* still for `org.gradle.usage`, if users pass `runtime` as a value, then things like `java-runtime` or `kotlin-runtime`
also match - more generally, anything ending in `-runtime` is accepted, alongside just `runtime`
* for `org.gradle.jvm.version`, users can pass a JVM major version, like `8` or `17`, and any version
higher or equal to that is accepted

Note that this list may evolve in the future, as Gradle Module support in coursier gets refined.

## Variant attributes as a substitute to Maven configuration

This section describes the interplay between Maven configurations (`compile`, `runtime`, …) and
variant attributes. This point is important to understand if you're obtaining surprising results
in resolutions involving both modules having `.module` files and modules having only POM files.

During resolution, variant attributes play the same role as Maven configurations: they help
pick the exact dependency set and artifacts of a given dependency. Internally, a dependency
has either variant attributes or a Maven configuration, but not both.

Note that the variant attribute set or the Maven configuration can be empty,
and these are assumed to be equivalent - and empty - in that case. By default, dependencies created via the API
or on the command-line have an empty configuration.

When a `.module` file is available for a dependency, 3 cases can happen:

* the dependency has an empty configuration or an empty variant attribute set. Then the default attribute
  set is used. It's the one passed via `--variant` on the command-line or with `Resolve.withDefaultVariantAttributes`
  or `Fetch.withDefaultVariantAttributes` via the API
* the dependency has variant attributes. In that case, these are used.
* the dependency has a configuration. In that case, an equivalent attribute set is computed [as below](#fallback-from-maven-configuration), and added
  to the default attribute set (the entries of former taking precedence over those of the latter)

When no module file is available, and a `.pom` file is used, 3 cases can happen:

* the dependency has an empty configuration or an empty variant attribute set. Then the default configuration
  is used. It can be passed on the command-line via `--configuration` or `-c`, like `-c compile`, and with
  `Resolve.withDefaultConfiguration` or `Fetch.withDefaultConfiguration` via the API. It defaults
  to `default(runtime)`, which is equivalent to `runtime` in practice.
* the dependency has variant attributes. In that case, an equivalent configuration is computed from the attributes
  [as below](#fallback-to-maven-configuration), or the default configuration is used if no configuration can be
  computed from variant attributes.
* the dependency has a configuration. In that case, its configuration is used.

### Fallback from Maven configuration

When all we have is a Maven configuration when variant attributes are needed, the following variant attributes
are used:

* `compile` configuration
    * `org.gradle.category=library`
    * `org.gradle.usage=api`
* `runtime` or `default` configuration
    * `org.gradle.category=library`
    * `org.gradle.usage=runtime`

* other configurations: no variant attributes

Note that the default attributes (passed on the command-line via `--variant`) are added to those, but
do not take precedence over them.

```scala mdoc:invisible
val compileEquivalent: Option[Map[String, coursier.core.VariantSelector.VariantMatcher]] = coursier.core.VariantSelector.ConfigurationBased(coursier.core.Configuration.compile).equivalentAttributesSelector.map(_.matchers)
val runtimeEquivalent: Option[Map[String, coursier.core.VariantSelector.VariantMatcher]] = coursier.core.VariantSelector.ConfigurationBased(coursier.core.Configuration.runtime).equivalentAttributesSelector.map(_.matchers)

assert(compileEquivalent.exists(_ == Map("org.gradle.category" -> coursier.core.VariantSelector.VariantMatcher.Library, "org.gradle.usage" -> coursier.core.VariantSelector.VariantMatcher.Api)))
assert(runtimeEquivalent.exists(_ == Map("org.gradle.category" -> coursier.core.VariantSelector.VariantMatcher.Library, "org.gradle.usage" -> coursier.core.VariantSelector.VariantMatcher.Runtime)))
```

### Fallback to Maven configuration

When all we have is variant attributes when a Maven configuration is needed, the following configuration is used:

* if `org.gradle.usage` is in the attribute set, and its value is `api` or ends with `-api`, then the
  `compile` configuration is used
* if `org.gradle.usage` is in the attribute set, and its value is `runtime` or ends with `-runtime`, then the
  `runtime` configuration is used
* in all other cases, no configuration is deduced from the variant attributes, and the default configuration
  ends up being used

## Consistency between Maven configuration and variant attributes

When passing default attributes and a default configuration to coursier, you should make sure that these are
consistent. That is, the fallback configuration of the default attributes should correspond to the
configuration, and the fallback attributes of the configuration ought to be contained in the default attributes.

That helps get a consistent set of dependencies (compile time dependencies, or runtime dependencies), rather
than a mix of both depending on whether dependencies have `.module` files available or not.

In practice, that means that if the `org.gradle.usage` attribute is `api`, you should ask for
`compile` as the default configuration; if the `org.gradle.usage` attribute is `runtime`, you should ask for
`runtime` as the default configuration (which is the default for the default configuration).

## Transitiveness of variant attributes

Note that the user-specified input attributes above aren't the only input attributes taken into account.
When a variant is picked during resolution, all its attributes are passed to its dependencies, which helps
disambiguate variants of these dependencies. The user-specified attributes are part of those, because
of the way these are passed around during resolution (see above), unless users manually specified variants
for specific root dependencies, using a syntax like this for dependencies
```text
org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,variant.org.gradle.usage=kotlin-runtime,variant.org.jetbrains.kotlin.platform.type=js,variant.org.jetbrains.kotlin.js.compiler=ir,variant.org.gradle.category=library
```

Note that you shouldn't use this syntax, unless you really know what you are doing. If its
attributes aren't part of the default attributes, and some transitive dependencies have only POM
files, the [interplay between variant attributes and Maven configuration](#variant-attributes-as-a-substitute-to-maven-configuration) may modify the resolution
result in unexpected way, with the variant being unexpectedly lost during resolution sometimes.
