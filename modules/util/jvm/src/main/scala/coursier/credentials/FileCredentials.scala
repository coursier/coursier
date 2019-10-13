package coursier.credentials

import java.io.{File, FileInputStream}
import java.util.Properties

import scala.collection.JavaConverters._
import dataclass.data

@data class FileCredentials(
  path: String,
  optional: Boolean = true
) extends Credentials {

  def get(): Seq[DirectCredentials] = {

    val f = new File(path)

    if (f.isFile) {

      val props = new Properties

      var fis: FileInputStream = null
      try {
        fis = new FileInputStream(f)
        props.load(fis)
      } finally {
        if (fis != null)
          fis.close()
      }

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
          throw new Exception(s"Property $prefix.password not found in $path")
        }

        val host = Option(props.getProperty(s"$prefix.host")).getOrElse {
          throw new Exception(s"Property $prefix.host not found in $path")
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

    } else if (optional)
      Nil
    else
      throw new Exception(s"Credential file $path not found")
  }
}
