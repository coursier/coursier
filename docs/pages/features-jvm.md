# JVM

Coursier can act as a JVM manager like [SDKMAN](https://sdkman.io) or [asdf](https://github.com/halcyon/asdf-java).

One notable feature of it is that it can give you the Java home of a JDK distribution without
actually "installing" it or "setting it up" on your system, but only relying on the various
coursier caches. That allows to manage several JDKs in parallel, with users using a main default
JDK, but using another one in a particular shell session, while apps can use some other JDKs.

The JVM management features of coursier rely on its [JVM index](https://github.com/coursier/jvm-index).
This index is updated on a regular basis, and list JDKs from various vendors, such as
[Temurin](https://adoptium.net/temurin), [GraalVM](https://www.graalvm.org),
[Zulu](https://www.azul.com/downloads/?package=jdk#zulu), [Liberica](https://bell-sw.com/libericajdk/),
etc. See [here](https://github.com/coursier/jvm-index?tab=readme-ov-file#available-jdks) for a more
up-to-date list.

The coursier JVM index is published both [on GitHub](https://github.com/coursier/jvm-index/blob/master/index.json) and [on Maven Central](https://repo1.maven.org/maven2/io/get-coursier/jvm/indices).
Both the API and the CLI of coursier default to the index on GitHub.

In more detail, when asked for a JVM, coursier first downloads a JVM index. It then look for an
entry in it adapted to the current OS and CPU architecture, and satisfying the user's request.
It then uses the [archive cache of coursier](features-archive-cache.md) to get an extracted version
of that archive on disk. The archive cache itself downloads the archive if needed with
[the standard coursier cache](features-cache.md), and extracts it under
[its own cache directory](features-archive-cache.md#cache-location).

So the JVMs live under [the archive cache directory](features-archive-cache.md#cache-location).
