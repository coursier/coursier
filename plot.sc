
import $ivy.`com.github.tototoshi::scala-csv:1.3.5`
import $ivy.`com.twitter::algebird-core:0.13.0`
import $ivy.`org.plotly-scala::plotly-almond:0.5.2`

import java.io.File
import java.time._

import com.twitter.algebird.Operators._
import plotly._
import plotly.element._
import plotly.layout._
import plotly.Plotly._

import com.github.tototoshi.csv._

val dir = new File("stats")

val data = for {

  year <- 2015 to Year.now(ZoneOffset.UTC).getValue
  month <- 1 to 12

  f = new File(dir, f"$year/$month%02d.csv")
  if f.exists()

  ym = YearMonth.of(year, month)

  elem <- CSVReader.open(f)
    .iterator
    .map(l => (ym, l(0), l(1).toInt))
    .toVector

} yield elem

val byModule = data
  .map { case (_, name, n) => name -> n }
  .sumByKey
  .toVector
  .sortBy(-_._2)

val byMonth = data
  .map { case (ym, _, n) => ym -> n }
  .sumByKey
  .toVector
  .sortBy(_._1)

// filter out the odd 2018-08
def byMonth0 = byMonth.filter { case (ym, _) => ym.getYear != 2018 || ym.getMonthValue != 8 }

def x = byMonth0.map(_._1).map { m =>
  plotly.element.LocalDateTime(m.getYear, m.getMonthValue, 1, 0, 0, 0)
}
def y = byMonth0.map(_._2)

Scatter(x, y).plot()


