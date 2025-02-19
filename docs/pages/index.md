# New coursier website

This website is in construction. See [the legacy website](https://get-coursier.io) for details
about other topics.


## Where to look?

The coursier cache backs basically all coursier features. It should be important for you
to understand how it works.

* cache

What user are you?

### Mill user

You use coursier via Mill. Mill uses coursier to get and manage JVM dependencies.
It also uses it to fetch JVMs, if you're using the Mill native launcher, or Mill's
per module JVM capabilities.

* archive cache
* JVM
* Gradle Modules
* BOM
* dependency format

### Scala user

You use the coursier CLI to setup Scala on your machine, following the instructions
of the scala-lang.org website.

* setup
* install
* bootstraps
* JVM

### Scala CLI user

You use coursier via Scala CLI. Scala CLI relies on many coursier features, most notably
JVM dependencies resolution and JVM management.

* dependency format
* archive cache
* JVM

### Scala / sbt user

You use coursier via sbt. sbt uses coursier to get and manage JVM dependencies.

* sbt particularities (relaxed reconciliation, disabled BOM support, no Gradle Module support, …)

### Bazel user

You use coursier via Bazel's rules_jvm_external. rules_jvm_external uses the coursier CLI
to fetch dependencies, and uses the coursier CLI's JSON report output to read its results.

* Gradle Modules
* BOM
* dependency format
* report

### JVM user

You use the coursier CLI to manage JVMs on your machine or from scripts.

* archive cache
* JVM

### Java user

You use the coursier Java API to download dependencies, and maybe JVMs or archives too.

* Java API
* archive cache
* JVM
