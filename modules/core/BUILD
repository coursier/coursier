scala_library(
    name = "core",
    dependencies = [
        "3rdparty/jvm:data-class",
        "3rdparty/jvm:fastParse",
        # TODO(wisechengyi) for some reason there is no compile error
        # and this is needed at runtime.
        "3rdparty/jvm:scala-xml",
        "util:util",
    ],
    sources = rglobs("jvm/src/main/*.scala", "shared/src/main/*.scala"),
)
