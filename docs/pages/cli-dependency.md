# Dependency

## Syntax

```text
org:name:version
```

## Bill Of Material

One can pass a Bill-Of-Material, or BOM, via dependencies, like
```text
com.google.protobuf:protobuf-java-util,bom=com.google.protobuf%protobuf-bom%4.28.1
```

Note the use of `%` rather than `:` to separate the components of the BOM coordinates.


## Gradle Module Variant

One can add Gradle Module attributes to a dependency, so that it checks if a Gradle Module
file exists for that dependency, and uses it to pick a "variant" of that dependency:
```text
org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,variant.org.gradle.usage=kotlin-runtime,variant.org.jetbrains.kotlin.platform.type=js,variant.org.jetbrains.kotlin.js.compiler=ir,variant.org.gradle.category=library
```

If we split / indent that dependency for readability, we get
```text
org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,
  variant.org.gradle.usage=kotlin-runtime,
  variant.org.jetbrains.kotlin.platform.type=js,
  variant.org.jetbrains.kotlin.js.compiler=ir,
  variant.org.gradle.category=library
```

Gradle Module attributes are key-value pairs. These are added as parameters to dependencies.
`variant.` is added as prefix to the key of the attribute, and the parameter value is the attribute
value.
