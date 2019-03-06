scala_library(
    name = "cli",
    dependencies = [
        "3rdparty/jvm:argonaut-shapeless",
        "3rdparty/jvm:caseapp",
        "bootstrap:bootstrap",
        "cache:cache",
        "core:core",
        "interop/scalaz:scalaz-interop",
        "coursier:coursier",
        "rules-parser:rules-parser",
        ":util",
    ],
    sources = rglobs(
        "src/main/scala-2.12/coursier/cli/*.scala",
        "src/main/contraband-scala/*.scala",
    ),
)

scala_library(
    name = "util",
    dependencies = [
        "3rdparty/jvm:argonaut-shapeless",
        "3rdparty/jvm:cats-core",
        "3rdparty/jvm:scala-native",
        "cache:cache",
        "core:core",
        "coursier:coursier",
    ],
    sources = globs("src/main/scala-2.12/coursier/cli/util/*.scala"),
)

jvm_binary(
    name = "coursier-cli",
    basename = "coursier-cli",
    dependencies = [
        ":cli",
    ],
    main = "coursier.cli.Coursier",
)
