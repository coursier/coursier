Sub-project `jcabi-aether` is a CLI tool that fetches and prints the dependencies of the
modules given on the command-line, using [Aether](https://github.com/eclipse/aether-core) and [jcabi](https://github.com/jcabi/jcabi-aether) to do the resolution.

Build it with
```scala
sbt jcabi-aether/pack
```

Run like
```scala
jcabi-aether/target/pack/bin/jcabi-aether "org.apache.spark:spark-core_2.10:1.4.0"
```
