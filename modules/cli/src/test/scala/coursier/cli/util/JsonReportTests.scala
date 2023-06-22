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
        getUrl = _ => Option(""),
        getFile = _ => Option(""),
        getChecksums = _ => None,
        exclusions = _ => Set.empty
      )

      assert(
        report == s"""{"conflict_resolution":{},"dependencies":[],"version":"${ReportNode.version}"}"""
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
        getUrl = _ => Option(""),
        getFile = _ => Option(""),
        getChecksums = _ => None,
        exclusions = _ => Set.empty
      )

      val reportJson = Parse.parse(report)

      val expectedReportJson = Parse.parse(
        s"""{
           |  "conflict_resolution": {},
           |  "dependencies": [
           |    {
           |      "coord": "a:reconciled",
           |      "file": "",
           |      "url": "",
           |      "directDependencies": [ "b:reconciled" ],
           |      "dependencies": [ "b:reconciled" ]
           |    },
           |    {
           |      "coord": "b:reconciled",
           |      "file": "",
           |      "url": "",
           |      "directDependencies": [],
           |      "dependencies": []
           |    }
           |  ],
           |  "version": "${ReportNode.version}"
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
        getUrl = _ => Option(""),
        getFile = _ => Option(""),
        getChecksums = _ => None,
        exclusions = _ => Set.empty
      )

      val reportJson = Parse.parse(report)

      val expectedReportJson = Parse.parse(
        s"""{
           |  "conflict_resolution": {},
           |  "dependencies": [
           |    { "coord": "a:reconciled", "file": "", "url": "", "directDependencies": [ "b:reconciled" ], "dependencies": [ "b:reconciled" ] },
           |    { "coord": "b:reconciled", "file": "", "url": "", "directDependencies": [], "dependencies": [] }
           |  ],
           |  "version": "${ReportNode.version}"
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
        getUrl = _ => Option(""),
        getFile = _ => Option(""),
        getChecksums = _ => None,
        exclusions = _ => Set.empty
      )

      val reportJson = Parse.parse(report)
      val expectedReportJson = Parse.parse(
        s"""{
           |  "conflict_resolution": {},
           |  "dependencies": [
           |    { "coord": "a:reconciled", "file": "", "url": "", "directDependencies": [ "b:reconciled" ], "dependencies": [ "b:reconciled" ] },
           |    { "coord": "b:reconciled", "file": "", "url": "", "directDependencies": [], "dependencies": [] }
           |  ],
           |  "version": "${ReportNode.version}"
           |}""".stripMargin
      )

      assert(reportJson == expectedReportJson)
    }

    test("JsonReport should prevent walking a tree with cycles") {
      val children =
        Map("a" -> Vector("b"), "b" -> Vector("c"), "c" -> Vector("a", "d"), "d" -> Vector.empty)
      val report = JsonReport[String](
        roots = Vector("a", "b", "c"),
        conflictResolutionForRoots = Map.empty
      )(
        children = children(_),
        reconciledVersionStr = s => s"$s:reconciled",
        requestedVersionStr = s => s"$s:requested",
        getUrl = _ => Option(""),
        getFile = _ => Option(""),
        getChecksums = _ => None,
        exclusions = _ => Set.empty
      )

      val reportJson = Parse.parse(report)
      val expectedReportJson = Parse.parse(
        s"""{
           |  "conflict_resolution": {},
           |  "dependencies": [
           |    { "coord": "a:reconciled", "file": "", "url": "", "directDependencies": [ "b:reconciled" ], "dependencies": [ "b:reconciled", "c:reconciled", "d:reconciled" ] },
           |    { "coord": "b:reconciled", "file": "", "url": "", "directDependencies": [ "c:reconciled" ], "dependencies": [ "a:reconciled", "c:reconciled", "d:reconciled" ] },
           |    { "coord": "c:reconciled", "file": "", "url": "", "directDependencies": [ "a:reconciled", "d:reconciled" ], "dependencies": [ "a:reconciled", "b:reconciled", "d:reconciled" ] }
           |  ],
           |  "version": "${ReportNode.version}"
           |}""".stripMargin
      )

      assert(reportJson == expectedReportJson)
    }

    test("JsonReport should include hashes") {
      val hashType  = "MD5"
      val hashValue = "value"
      val children  = Map("a" -> Vector.empty)
      val report = JsonReport[String](
        roots = Vector("a"),
        conflictResolutionForRoots = Map.empty
      )(
        children = children(_),
        reconciledVersionStr = s => s,
        requestedVersionStr = s => s,
        getUrl = _ => Option(""),
        getFile = _ => Option(""),
        getChecksums = _ => Some(Map(hashType -> hashValue)),
        exclusions = _ => Set.empty
      )
      val reportJson = Parse.parse(report)
      val expectedReportJson = Parse.parse(
        s"""{
           |  "conflict_resolution": {},
           |  "dependencies": [
           |    {
           |      "coord": "a",
           |      "file": "",
           |      "url": "",
           |      "directDependencies": [],
           |      "dependencies": [],
           |      "checksums": {
           |         "$hashType": "$hashValue"
           |      }
           |    }
           |  ],
           |  "version": "${ReportNode.version}"
           |}""".stripMargin
      )

      assert(reportJson == expectedReportJson)
    }
  }

}
