package coursier.cli.config

import caseapp.core.RemainingArgs
import caseapp.core.app.Command
import coursier.paths.CoursierPaths

import java.nio.file.{Files, Paths}

import scala.cli.config.{ConfigDb, Key, Keys, PasswordOption, Secret}
import scala.collection.mutable.ListBuffer

object Config extends Command[ConfigOptions] {
  override def hidden = true

  private def mask = "****"

  /** How to print a password option without leaking its value
    *
    * Passwords stored as is (`value:…`) are masked, while references to environment variables,
    * files, or commands are printed as is, as they're not secrets themselves.
    */
  private def maskedPasswordOption(passwordOption: PasswordOption): String =
    passwordOption match {
      case _: PasswordOption.Value => s"value:$mask"
      case other                   => other.asString.value
    }

  /** Lines to print for a config entry
    *
    * @param showSecrets
    *   whether to print passwords in clear, rather than masking them
    */
  def valueLines(
    db: ConfigDb,
    entry: Key[?],
    showSecrets: Boolean
  ): Option[Seq[String]] =
    entry match {
      case cred: Key.RepositoryCredentialsEntry =>
        // We're basically inlining db.getAsString(entry) here, except
        // we call PasswordOption#get() rather than PasswordOption#asString
        // on user and password below (password only if showSecrets is true)
        db.get(cred).fold(e => throw new Exception(e), identity).map { value =>
          value
            .zipWithIndex
            .map {
              case (cred, idx) =>
                val prefix = s"configRepo$idx."

                val lines = new ListBuffer[String]
                if (cred.host.nonEmpty)
                  lines += s"${prefix}host=${cred.host}"
                for (u <- cred.user)
                  lines += s"${prefix}username=${u.get().value}"
                for (p <- cred.password)
                  lines += s"${prefix}password=${if (showSecrets) p.get().value else mask}"
                for (r <- cred.realm)
                  lines += s"${prefix}realm=$r"
                for (b <- cred.httpsOnly)
                  lines += s"${prefix}https-only=$b"
                for (b <- cred.matchHost)
                  lines += s"${prefix}auto=$b"
                for (b <- cred.passOnRedirect)
                  lines += s"${prefix}pass-on-redirect=$b"

                lines.map(_ + System.lineSeparator()).mkString
            }
        }
      case cred: Key.PublishCredentialsEntry if !showSecrets =>
        // PublishCredentials#asString calls PasswordOption#get() on the password
        db.get(cred).fold(e => throw new Exception(e), identity).map { value =>
          value.map { cred =>
            cred
              .copy(password = cred.password.map(_ => PasswordOption.Value(Secret(mask))))
              .asString
          }
        }
      case _ =>
        val valueOpt = db.getAsString(entry).fold(e => throw new Exception(e), identity)
        if (entry.isPasswordOption && !showSecrets)
          valueOpt.map { values =>
            values.map { v =>
              PasswordOption.parse(v).fold(_ => mask, maskedPasswordOption)
            }
          }
        else
          valueOpt
    }

  def run(options: ConfigOptions, args: RemainingArgs): Unit = {

    val configPath = options.configFile
      .filter(_.trim.nonEmpty)
      .map(Paths.get(_))
      .getOrElse(CoursierPaths.scalaConfigFile())

    if (options.dump) {
      val content = Files.readAllBytes(configPath)
      System.out.write(content)
    }
    else {
      val db = ConfigDb.open(configPath)
        .fold(e => throw new Exception(e), identity)

      def unrecognizedKey(key: String): Nothing = {
        System.err.println(s"Error: unrecognized key $key")
        sys.exit(1)
      }

      args.all match {
        case Seq() =>
          System.err.println("No argument passed")
          sys.exit(1)
        case Seq(name, values @ _*) =>
          val keysMap = Keys.map ++
            Seq(Keys.repositoriesMirrors, Keys.defaultRepositories, Keys.repositoryCredentials).map(
              e => e.fullName -> e
            )
          keysMap.get(name) match {
            case None        => unrecognizedKey(name)
            case Some(entry) =>
              if (values.isEmpty)
                if (options.unset) {
                  db.remove(entry)
                  db.save(configPath).fold(e => throw new Exception(e), identity)
                }
                else {
                  val valueOpt = valueLines(db, entry, showSecrets = options.password)
                  valueOpt match {
                    case Some(value) =>
                      for (v <- value)
                        if (options.password && entry.isPasswordOption)
                          PasswordOption.parse(v) match {
                            case Left(err) =>
                              System.err.println(err)
                              sys.exit(1)
                            case Right(passwordOption) =>
                              val password = passwordOption.getBytes
                              System.out.write(password.value)
                          }
                        else
                          println(v)
                    case None =>
                    // logger.debug(s"No value found for $name")
                  }
                }
              else {
                val finalValues =
                  if (options.passwordValue && entry.isPasswordOption)
                    values.map { input =>
                      PasswordOption.parse(input) match {
                        case Left(err) =>
                          System.err.println(err)
                          sys.exit(1)
                        case Right(passwordOption) =>
                          PasswordOption.Value(passwordOption.get()).asString.value
                      }
                    }
                  else
                    values

                db.setFromString(entry, finalValues).fold(e => throw new Exception(e), identity)
                db.save(configPath).fold(e => throw new Exception(e), identity)
              }
          }
      }
    }
  }
}
