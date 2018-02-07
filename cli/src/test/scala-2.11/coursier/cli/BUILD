junit_tests(
    name = "test",
    dependencies = [
        ":lib",
        "3rdparty/jvm:scalatest",
        "cli/src/main/scala-2.11:cli",
    ],
    sources = ["CliFetchIntegrationTest.scala", "CliUnitTest.scala"],
)

scala_library(
    name='lib',
    dependencies = [
        "3rdparty/jvm:caseapp",
    ],
    sources = ["CliTestLib.scala"],
)
