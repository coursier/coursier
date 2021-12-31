package coursier.cli.util

import argonaut.Parse
import utest._

object JsonReportTests extends TestSuite {
  val tests = Tests {
    test("empty JsonReport should be empty") {
      val report: String = JsonReport[String](Vector.empty, Map())(
        children = _ => Vector.empty,
        reconciledVersionStr = _ => "",
        requestedVersionStr = _ => "",
        getFile = _ => Option(""),
        exclusions = _ => Set.empty
      )

      assert(
        report == "{\"conflict_resolution\":{},\"dependencies\":[],\"version\":\"0.1.0\"}"
      )
    }

    test("JsonReport containing two deps should not be empty") {
      val children = Map("a" -> Seq("b"), "b" -> Seq())
      val report: String = JsonReport[String](
        roots = Vector("a", "b"),
        conflictResolutionForRoots = Map()
      )(
        children = children(_).toVector,
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

    test(
      "JsonReport containing two deps should be sorted alphabetically regardless of input order"
    ) {
      val children = Map("a" -> Seq("b"), "b" -> Seq())
      val report: String = JsonReport[String](
        roots = Vector("b", "a"),
        conflictResolutionForRoots = Map()
      )(
        children = children(_).toVector,
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

    test("JsonReport should prevent walking a tree in which a dependency depends on itself") {
      val children = Map("a" -> Vector("a", "b"), "b" -> Vector.empty)
      val report = JsonReport[String](
        roots = Vector("a", "b"),
        conflictResolutionForRoots = Map.empty
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

    test("JsonReport should prevent walking a tree with cycles") {
      val children = Map("a" -> Vector("b"), "b" -> Vector("a"))
      val report = JsonReport[String](
        roots = Vector("a", "b"),
        conflictResolutionForRoots = Map.empty
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
          |    { "coord": "b:reconciled", "file": "", "directDependencies": [ "a:reconciled" ], "dependencies": [ "a:reconciled" ] }
          |  ],
          |  "version": "0.1.0"
          |}""".stripMargin
      )

      assert(reportJson == expectedReportJson)
    }
  }
}
