scala_library(
    name = "cli",
    dependencies = [
        "3rdparty/jvm:argonaut-shapeless",
        "3rdparty/jvm:caseapp",
        "bootstrap:bootstrap",
        "cache:cache",
        "core:core",
        "extra/src/main/scala/coursier:fallback-deps-repo",
        "extra/src/main/scala/coursier/extra:extra",
        "extra/src/main/scala-2.12/coursier/extra:native",
        "interop/scalaz:scalaz-interop",
        "coursier:coursier",
        "rules-parser:rules-parser",
        ":util",
    ],
    sources = rglobs(
        "coursier/cli/*.scala",
    ),
)

scala_library(
    name = "util",
    dependencies = [
        "3rdparty/jvm:argonaut-shapeless",
        "3rdparty/jvm:cats-core",
        "cache:cache",
        "core:core",
    ],
    sources = globs("coursier/cli/util/*.scala"),
)

jvm_binary(
    name = "coursier-cli",
    basename = "coursier-cli",
    dependencies = [
        ":cli",
    ],
    main = "coursier.cli.Coursier",
)
