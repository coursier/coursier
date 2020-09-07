package coursier.cli.util

import argonaut.Parse
import coursier.cli.CliTestLib
import utest._

object JsonReportTest extends TestSuite with CliTestLib {
  val tests = Tests {
  test("empty JsonReport should be empty") {
    val report: String = JsonReport[String](IndexedSeq(), Map())(
      children = _ => Seq(),
      reconciledVersionStr = _ => "",
      requestedVersionStr = _ => "",
      getFile = _ => Option(""),
      exclusions = _ => Set.empty
    )

    assert(
      report == "{\"conflict_resolution\":{},\"dependencies\":[],\"version\":\"0.1.0\"}")
  }

  test("JsonReport containing two deps should not be empty") {
    val children = Map("a" -> Seq("b"), "b" -> Seq())
    val report: String = JsonReport[String](
      roots = IndexedSeq("a", "b"),
      conflictResolutionForRoots = Map()
    )(
      children = children(_),
      reconciledVersionStr = s => s"$s:reconciled",
      requestedVersionStr = s => s"$s:requested",
      getFile = _ => Option(""),
      exclusions = _ => Set.empty
    )

    val reportJson = Parse.parse(report)

    val expectedReportJson = Parse.parse(
      """{
        |  "conflict_resolution": {},
        |  "dependencies": [
        |    {
        |      "coord": "a:reconciled",
        |      "file": "",
        |      "directDependencies": [ "b:reconciled" ],
        |      "dependencies": [ "b:reconciled" ]
        |    },
        |    {
        |      "coord": "b:reconciled",
        |      "file": "",
        |      "directDependencies": [],
        |      "dependencies": []
        |    }
        |  ],
        |  "version": "0.1.0"
        |}""".stripMargin
    )

    assert(reportJson == expectedReportJson)
  }
  test("JsonReport containing two deps should be sorted alphabetically regardless of input order") {
    val children = Map("a" -> Seq("b"), "b" -> Seq())
    val report: String = JsonReport[String](
      roots = IndexedSeq( "b", "a"),
      conflictResolutionForRoots = Map()
    )(
      children = children(_),
      reconciledVersionStr = s => s"$s:reconciled",
      requestedVersionStr = s => s"$s:requested",
      getFile = _ => Option(""),
      exclusions = _ => Set.empty
    )

    val reportJson = Parse.parse(report)

    val expectedReportJson = Parse.parse(
      """{
        |  "conflict_resolution": {},
        |  "dependencies": [
        |    { "coord": "a:reconciled", "file": "", "directDependencies": [ "b:reconciled" ], "dependencies": [ "b:reconciled" ] },
        |    { "coord": "b:reconciled", "file": "", "directDependencies": [], "dependencies": [] }
        |  ],
        |  "version": "0.1.0"
        |}""".stripMargin
    )

    assert(reportJson == expectedReportJson)
  }
  }
}
