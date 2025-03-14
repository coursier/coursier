# Dependency

```scala mdoc:reset-object:invisible
import coursier._
```

One can specify input dependencies of a resolution via the `Dependency` class, like
```scala mdoc:silent
val module = Module(Organization("my-org"), ModuleName("my-name"), Map.empty)
val versionConstraint = VersionConstraint("1.2.3")
Dependency(module, versionConstraint)
```

In Scala 2.x, string interpolators allow to specify dependencies using the same syntax
than [on the CLI](cli-dependency.md):
```scala mdoc:silent
dep"my-org:my-name:1.2.3"
```

## Bill Of Material

```scala mdoc:reset-object:invisible
import coursier._
```

Bills Of Material, or BOMs, allow to:

* fill input dependencies' versions if these are empty
* override versions of transitive dependencies

To pass a BOM to a dependency, you can call `addBom` on a `Dependency`:
```scala mdoc:silent
val module = Module(Organization("my-org"), ModuleName("my-name"), Map.empty)
val versionConstraint = VersionConstraint("1.2.3")
val bomModule = Module(Organization("my-org"), ModuleName("my-bom"), Map.empty)
val bomVersionConstraint = VersionConstraint("1.0.1")
Dependency(module, versionConstraint)
  .addBom(BomDependency(bomModule, bomVersionConstraint))
```
 In Scala 2.x, on can use convenient string interpolators:
```scala mdoc:silent
dep"my-org:my-name:1.2.3"
  .addBom(mod"my-org:my-bom", bomVersionConstraint)
```
or
```scala mdoc:silent
dep"my-org:my-name:1.2.3"
  .addBom(dep"my-org:my-bom:1.0.1".asBomDependency)
```

## Gradle Module Variant

```scala mdoc:reset-object:invisible
import coursier._
```

### Enabling Gradle Module support

Gradle Module support needs to be enabled, either for individual Maven repositories, like
```scala mdoc:silent
Fetch()
  .withRepositories(
    Fetch().repositories.map {
      case m: MavenRepository =>
        m.withCheckModule(true)
      case other => other
    }
  )
```
or for all repositories at once, like
```scala mdoc:silent
Fetch().withGradleModuleSupport(true)
```

### Adding attributes to dependencies

Variant attributes can be specified once for all dependencies, with
```scala mdoc:silent
Fetch()
```

One can ask for specific Gradle Module variants of a dependency, like
```scala mdoc:silent
import coursier.core.VariantSelector.VariantMatcher

dep"my-org:my-name:1.2.3".addVariantAttributes(
  "org.gradle.usage"                   -> VariantMatcher.Runtime,
  "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("js"),
  "org.jetbrains.kotlin.js.compiler"   -> VariantMatcher.Equals("ir"),
  "org.gradle.category"                -> VariantMatcher.Library
)
```

Note that calling `Dependency#addVariantAttributes` discards any configuration
details the dependency might have previously in its `variantSelector` field (see below).

### Gradle Module Variants versus Maven Configuration

A `Dependency` instance can ask either for a Maven Configuration, or for
Gradle Module variants. By default, `Dependency` instances ask for the empty
configuration, which is interpreted internally as no configuration nor variants.
During resolution, that empty configuration gets substituted by the default
configuration pulled by the resolution (whose value is the `default(runtime)`
configuration, meaning if direct dependencies have a configuration named `default`,
it gets pulled - else, the configuration named `runtime` gets pulled).

The dependency field handling both Gradle Module Variants and Maven Configurations
is `Dependency#variantSelector`, whose type is `coursier.core.VariantSelector`:
```scala mdoc
dep"my-org:my-name:1.2.3".variantSelector
```

`VariantSelector` is an ADT, and has 2 cases:

* `VariantSelector.ConfigurationBased`, that corresponds to Maven Configurations
* `VariantSelector.AttributesBased`, that corresponds to Gradle Module Variants

### Maven Configuration fallback

coursier tries to download Gradle Module files if support for Gradle Modules is enabled. Such
a support can be enabled

If a repository doesn't have a Gradle Module file for such dependency, but has a POM
file for it, coursier falls back to using Maven Configurations for this module. The configuration
it falls back to is computed by the `VariantSelector.AttributesBased#equivalentConfiguration` method.

As of writing this, it does the following:

* if the dependency has an `org.gradle.usage` attribute whose value is `java-api` or `kotlin-api`, it falls back to the `compile` Maven Configuration
* if the dependency has an `org.gradle.usage` attribute whose value is `java-runtime` or `kotlin-runtime`, it falls back to the `runtime` Maven Configuration

If none of those conditions are met, during resolution, coursier falls back to the default configuration
of the resolution (whose default value is `default(runtime)`).
