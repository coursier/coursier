scala_library(
    name = "cli",
    dependencies = [
        "3rdparty/jvm:argonaut-shapeless",
        "3rdparty/jvm:caseapp",
        "3rdparty/jvm:cats-core",
        "3rdparty/jvm:monadless-cats",
        "3rdparty/jvm:monadless-stdlib",
        "bootstrap:bootstrap",
        "cache:cache",
        "core:core",
        "interop/scalaz:scalaz-interop",
        "coursier:coursier",
        "publish:publish",
    ],
    sources = rglobs(
        "src/main/scala/coursier/cli/*.scala",
        "src/main/contraband-scala/*.scala",
    ),
)

jvm_binary(
    name = "coursier-cli",
    basename = "coursier-cli",
    dependencies = [
        ":cli",
    ],
    main = "coursier.cli.Coursier",
)
