package coursier.clitests

import java.io.ByteArrayOutputStream
import java.net.{HttpURLConnection, ServerSocket, URL}
import java.nio.charset.StandardCharsets
import java.util.{Arrays, Base64, UUID}

import utest._

abstract class ServerTests extends TestSuite {

  def launcher: String

  private def freePort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  private def waitForServer(host: String, port: Int, maxAttempts: Int = 50): Unit = {
    var attempts = 0
    var ready    = false
    while (!ready && attempts < maxAttempts)
      try {
        val conn = new URL(s"http://$host:$port/").openConnection()
          .asInstanceOf[HttpURLConnection]
        conn.setConnectTimeout(200)
        conn.setReadTimeout(200)
        conn.getResponseCode
        ready = true
      }
      catch {
        case _: Exception =>
          attempts += 1
          Thread.sleep(200)
      }
    if (!ready)
      sys.error(s"Server not ready after $maxAttempts attempts")
  }

  private def postJson(
    host: String,
    port: Int,
    user: String,
    password: String,
    path: String,
    body: String
  ): (Int, String) = {
    val conn = new URL(s"http://$host:$port$path").openConnection()
      .asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setRequestProperty("Content-Type", "application/json")
    val credentials = Base64.getEncoder.encodeToString(
      s"$user:$password".getBytes(StandardCharsets.UTF_8)
    )
    conn.setRequestProperty("Authorization", s"Basic $credentials")
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    conn.getOutputStream.write(bytes)
    conn.getOutputStream.close()
    val code = conn.getResponseCode
    val stream =
      if (code >= 400) conn.getErrorStream
      else conn.getInputStream
    val response =
      if (stream != null) {
        val baos = new ByteArrayOutputStream
        val buf  = new Array[Byte](8192)
        var n    = 0
        while ({ n = stream.read(buf); n != -1 })
          baos.write(buf, 0, n)
        stream.close()
        new String(baos.toByteArray, StandardCharsets.UTF_8)
      }
      else ""
    (code, response)
  }

  private val pomUrl =
    "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.8.2/scala3-library_3-3.8.2.pom"
  private val otherPomUrl =
    "https://repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.8.1/scala3-library_3-3.8.1.pom"

  val tests = Tests {
    test("server get POM") {
      val tmpDir      = os.temp.dir(prefix = "coursier-cli-test")
      val serverCache = tmpDir / "server-cache"
      os.makeDir(serverCache)
      val port = freePort()
      val host = "localhost"

      val user     = UUID.randomUUID().toString
      val password = UUID.randomUUID().toString

      val serverProcess = os.proc(
        launcher,
        "server",
        "--host",
        host,
        "--port",
        port.toString,
        "--user",
        "env:COURSIER_CACHE_SERVER_USER",
        "--password",
        "env:COURSIER_CACHE_SERVER_PASSWORD"
      )
        .spawn(
          env = Map(
            "COURSIER_CACHE"                 -> serverCache.toString,
            "COURSIER_CACHE_SERVER_USER"     -> user,
            "COURSIER_CACHE_SERVER_PASSWORD" -> password
          ),
          stderr = os.Inherit
        )

      try {
        waitForServer(host, port)

        val requestBody          = s"""{"artifact":{"url":"$pomUrl"}}"""
        val (code, responseBody) = postJson(host, port, user, password, "/get", requestBody)
        assert(code == 200)

        val cachedFileViaHttpReq = {
          val ujsonResponse = ujson.read(responseBody)
          val pathOpt = ujsonResponse.obj
            .get("path")
            .flatMap(v => if (v.isNull) None else Some(v.str))
          val errorOpt = ujsonResponse.obj
            .get("error")
            .flatMap(v => if (v.isNull) None else Some(v))
          assert(errorOpt.isEmpty)
          assert(pathOpt.nonEmpty)

          val cachedFile = serverCache / os.SubPath(pathOpt.get)
          assert(os.exists(cachedFile))
          cachedFile
        }

        // Also fetch the same file with "cs get" using the standard cache
        val csGetPath = os.Path(
          os.proc(launcher, "get", pomUrl).call().out.trim()
        )
        assert(os.exists(csGetPath))
        assert(Arrays.equals(os.read.bytes(cachedFileViaHttpReq), os.read.bytes(csGetPath)))

        val cachedViaRemoteCache = {
          val path = os.proc(launcher, "get", otherPomUrl)
            .call(
              env = Map(
                "COURSIER_CACHE"                 -> serverCache.toString,
                "COURSIER_CACHE_SERVER"          -> s"http://$host:$port",
                "COURSIER_CACHE_SERVER_USER"     -> user,
                "COURSIER_CACHE_SERVER_PASSWORD" -> password
              )
            )
            .out.trim()
          os.Path(path)
        }
        assert(os.exists(cachedViaRemoteCache))
        assert(cachedViaRemoteCache.startsWith(serverCache))

        val csGetOtherPath = os.Path(
          os.proc(launcher, "get", otherPomUrl).call().out.trim()
        )
        assert(os.exists(csGetOtherPath))
        assert(Arrays.equals(os.read.bytes(cachedViaRemoteCache), os.read.bytes(csGetOtherPath)))
      }
      finally {
        serverProcess.destroy()
        os.remove.all(tmpDir)
      }
    }
  }
}
