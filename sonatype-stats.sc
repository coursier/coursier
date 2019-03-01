#!/usr/bin/env amm

import $ivy.`com.softwaremill.sttp::core:1.5.10`

import java.nio.file._
import java.time.{YearMonth, ZoneOffset}

import com.softwaremill.sttp.quick._
import upickle.default._
import ujson.{read => _, _}

val base = Paths.get("stats")

val proj = sys.env("SONATYPE_PROJECT") // organization one was granted write access to
val organization = sys.env.getOrElse("SONATYPE_PROJECT", proj) // actual organization used for publishing (must have proj as prefix)

def sonatypeUser = sys.env("SONATYPE_USERNAME")
def sonatypePassword: String = sys.env("SONATYPE_PASSWORD")

val projResp = sttp
  .auth.basic(sonatypeUser, sonatypePassword)
  .header("Accept", "application/json")
  .get(uri"https://oss.sonatype.org/service/local/stats/projects")
  .send()

if (!projResp.isSuccess)
  sys.error("Error getting project list: " + projResp.statusText)

case class Elem(id: String, name: String)
implicit val elemRW: ReadWriter[Elem] = macroRW

val respJson = ujson.read(projResp.body.right.get)
val projectIds = read[Seq[Elem]](respJson("data"))
  .map(e => e.name -> e.id)
  .toMap

val projId = projectIds(proj)

val start = YearMonth.now(ZoneOffset.UTC)
val monthYears =
  Iterator.iterate(start)(_.minusMonths(1L))

def fileFor(monthYear: YearMonth): Path = {
  val year = monthYear.getYear
  val month = monthYear.getMonth.getValue
  base.resolve(f"$year%04d/$month%02d.csv")
}

def exists(monthYear: YearMonth): Boolean =
  Files.isRegularFile(fileFor(monthYear))

def write(monthYear: YearMonth, content: String): Unit = {
  println(s"Writing $monthYear (${content.length} B)")
  val f = fileFor(monthYear)
  Files.createDirectories(f.getParent)
  Files.write(f, content.getBytes("UTF-8"))
}

val process = monthYears
  .filter { monthYear =>
    !exists(monthYear)
  }
  .map { monthYear =>

    val year = monthYear.getYear
    val month = monthYear.getMonth.getValue

    println(s"Getting $monthYear")

    val statResp = sttp
      .auth.basic(sonatypeUser, sonatypePassword)
      .header("Accept", "application/json")
      .get(uri"https://oss.sonatype.org/service/local/stats/slices_csv?p=$projId&g=$organization&a=&t=raw&from=${f"$year%04d$month%02d"}&nom=1")
      .send()

    if (!statResp.isSuccess)
      sys.error("Error getting project stats: " + statResp.statusText)

    val stats = statResp.body.right.get.trim

    if (stats.nonEmpty)
      write(monthYear, stats)
    else
      println(s"Empty response at $monthYear")

    monthYear -> stats.nonEmpty
  }

val cutOff = start.minusMonths(4L)
val processed = process
  .takeWhile {
    case (monthYear, nonEmpty) =>
      nonEmpty || monthYear.compareTo(cutOff) >= 0
  }
  .length

System.err.println(s"Processed $processed months")

