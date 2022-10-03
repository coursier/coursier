package coursier.cache.internal

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import coursier.credentials.DirectCredentials

import scala.cli.config.Key
import java.nio.file.Path
import scala.cli.config.PasswordOption
import scala.collection.mutable.ListBuffer

object TmpConfig {

  private final case class AsJson(
    host: String,
    user: Option[String] = None,
    password: Option[String] = None,
    realm: Option[String] = None,
    optional: Option[Boolean] = None,
    matchHost: Option[Boolean] = None,
    httpsOnly: Option[Boolean] = None,
    passOnRedirect: Option[Boolean] = None
  ) {
    def credentials: DirectCredentials = {
      var cred = DirectCredentials().withHost(host)
      for (u <- user)
        PasswordOption.parse(u) match {
          case Left(error) =>
            throw new Exception(
              s"Malformed repository credentials user value (expected 'value:…', or 'file:/path', or 'env:ENV_VAR_NAME')"
            )
          case Right(value) =>
            cred = cred.withUsername(value.get().value)
        }
      for (p <- password if p.nonEmpty)
        PasswordOption.parse(p) match {
          case Left(error) =>
            throw new Exception(
              s"Malformed repository credentials password value (expected 'value:…', or 'file:/path', or 'env:ENV_VAR_NAME')"
            )
          case Right(value) =>
            cred = cred.withPassword(value.get().value)
        }
      for (r <- realm)
        cred = cred.withRealm(r)
      for (opt <- optional)
        cred = cred.withOptional(opt)
      for (v <- matchHost)
        cred = cred.withMatchHost(v)
      for (v <- httpsOnly)
        cred = cred.withHttpsOnly(v)
      for (v <- passOnRedirect)
        cred = cred.withPassOnRedirect(v)
      cred
    }
  }

  val credentialsKey: Key[List[DirectCredentials]] = new Key[List[DirectCredentials]] {

    private def asJson(credentials: DirectCredentials): AsJson =
      AsJson(
        credentials.host,
        credentials.usernameOpt,
        credentials.passwordOpt.map(_.value),
        credentials.realm,
        Some(credentials.optional)
          .filter(_ != DirectCredentials().optional),
        Some(credentials.matchHost)
          .filter(_ != DirectCredentials().matchHost),
        Some(credentials.httpsOnly)
          .filter(_ != DirectCredentials().httpsOnly),
        Some(credentials.passOnRedirect)
          .filter(_ != DirectCredentials().passOnRedirect)
      )
    private val codec: JsonValueCodec[List[AsJson]] =
      JsonCodecMaker.make

    def prefix = Seq("repositories")
    def name   = "credentials"

    def parse(json: Array[Byte]): Either[Key.EntryError, List[DirectCredentials]] =
      try Right(readFromArray(json)(codec).map(_.credentials))
      catch {
        case e: JsonReaderException =>
          Left(new Key.JsonReaderError(e))
      }
    def write(value: List[DirectCredentials]): Array[Byte] =
      writeToArray(value.map(asJson))(codec)

    def asString(value: List[DirectCredentials]): Seq[String] =
      sys.error("Inline credentials not supported, please manually edit the config file")
    def fromString(values: Seq[String]): Either[Key.MalformedValue, List[DirectCredentials]] =
      sys.error("Inline credentials not accepted, please manually edit the config file")
  }
}
