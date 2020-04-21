package coursier.cli.util

import argonaut.Parse
import coursier.cli.CliTestLib
import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JsonReportTest extends AnyFlatSpec with CliTestLib {
  "empty JsonReport" should "be empty" in {
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

  "JsonReport containing two deps" should "not be empty" in {
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
  "JsonReport containing two deps" should "be sorted alphabetically regardless of input order" in {
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
