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


## Fetching artifacts without POM files

Some artifacts do not have a POM or Ivy file in the repository. Coursier supports fetching
such artifacts directly via the `url=` parameter:
```text
org:name:version,url=https%3A%2F%2Fexample.com%2Fpath%2Fto%2Fmy-artifact-1.0.jar
```

Note that the URL must be URL-encoded. The `url` parameter instructs coursier to fetch the
artifact from the given URL directly, bypassing the usual POM resolution. For example, to
fetch a JAR at `https://example.com/my-lib/1.0/my-lib-1.0.jar`:
```text
com.example:my-lib:1.0,url=https%3A%2F%2Fexample.com%2Fmy-lib%2F1.0%2Fmy-lib-1.0.jar
```

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
