#!/usr/bin/env amm

import $ivy.`org.http4s::http4s-blaze-server:0.17.5`
import $ivy.`org.http4s::http4s-dsl:0.17.5`
import $ivy.`org.http4s::http4s-server:0.17.5`

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers._
import org.http4s.server.blaze.BlazeBuilder


val host = sys.env("SERVER_HOST")
val port = sys.env("SERVER_PORT").toInt

val redirectTo = Uri.unsafeFromString(sys.env("REDIRECT_TO"))

def service(host: String, port: Int, redirectTo: Uri) = HttpService {
  case (method @ (GET | HEAD)) -> Path(path @ _*) =>
    println(s"${method.name} ${path.mkString("/")}")
    TemporaryRedirect(path.foldLeft(redirectTo)(_ / _))
}

val server = BlazeBuilder
  .bindHttp(port, host)
  .mountService(service(host, port, redirectTo))
  .start
  .unsafeRun()

println(s"Listening on http://$host:$port")

if (System.console() == null)
  while (true) Thread.sleep(60000L)
else {
  println("Press Ctrl+D to exit")
  while (System.in.read() != -1) {}
}

server.shutdown.unsafeRun()
