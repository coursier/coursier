package coursier.credentials

import java.io.{File, FileInputStream, StringReader}
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.util.Properties

import dataclass.data

import scala.collection.JavaConverters._

@data class FileCredentials(
  path: String,
  optional: Boolean = true
) extends Credentials {

  def get(): Seq[DirectCredentials] = {

    val f = Paths.get(path)

    if (Files.isRegularFile(f)) {
      val content = new String(Files.readAllBytes(f), Charset.defaultCharset())
      FileCredentials.parse(content, path)
    } else if (optional)
      Nil
    else
      throw new Exception(s"Credential file $path not found")
  }
}

object FileCredentials {

  def parse(content: String, origin: String): Seq[DirectCredentials] = {

    val props = new Properties
    props.load(new StringReader(content))

    val userProps = props
      .propertyNames()
      .asScala
      .map(_.asInstanceOf[String])
      .filter(_.endsWith(".username"))
      .toVector

    userProps.map { userProp =>
      val prefix = userProp.stripSuffix(".username")

      val user = props.getProperty(userProp)
      val password = Option(props.getProperty(s"$prefix.password")).getOrElse {
        throw new Exception(s"Property $prefix.password not found in $origin")
      }

      val host = Option(props.getProperty(s"$prefix.host")).getOrElse {
        throw new Exception(s"Property $prefix.host not found in $origin")
      }

      val realmOpt = Option(props.getProperty(s"$prefix.realm")) // filter if empty?

      val matchHost = Option(props.getProperty(s"$prefix.auto")).fold(false)(_.toBoolean)
      val httpsOnly = Option(props.getProperty(s"$prefix.https-only")).fold(true)(_.toBoolean)
      val passOnRedirect = Option(props.getProperty(s"$prefix.pass-on-redirect")).fold(false)(_.toBoolean)

      DirectCredentials(host, user, password)
        .withRealm(realmOpt)
        .withMatchHost(matchHost)
        .withHttpsOnly(httpsOnly)
        .withPassOnRedirect(passOnRedirect)
    }
  }

}
