package coursier.cli.config

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{AccessDeniedException, Files, Path, Paths}

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.Properties

import caseapp.core.RemainingArgs
import caseapp.core.app.Command
import scala.cli.config.{ConfigDb, ErrorMessages, Key, PasswordOption, Secret}

import coursier.paths.CoursierPaths

object Config extends Command[ConfigOptions] {
  override def hidden = true

  /** An error to report to users, as is, without a stack trace */
  final class ConfigError(message: String) extends Exception(message)

  private def configDirPermissionsHint(configPath: Path): Option[String] =
    Option(configPath.getParent).filter { dir =>
      !Properties.isWin && Files.isDirectory(dir) && {
        val perms = Files.getPosixFilePermissions(dir).asScala
        val ownerPerms = Set(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE
        )
        perms.exists(p => !ownerPerms.contains(p))
      }
    }.map { dir =>
      s"Restrict the directory permissions with 'chmod 700 $dir', or pass --config-file " +
        "with a file in another directory."
    }

  /** Converts an error of the scala-cli config library to a [[ConfigError]]
    *
    * @param configPath
    *   path of the config file, to add hints to the error message
    */
  def configError(e: Exception, configPath: Path): ConfigError = {
    // the cause of format errors is a JSON parsing error, saying what's wrong in the file
    // (its first line, the next ones being a hex dump of the file)
    val causeMessage = Option(e.getCause)
      .flatMap(c => Option(c.getMessage))
      .flatMap(_.linesIterator.find(_.trim.nonEmpty))
      .map(_.stripSuffix(", buf:"))
    val message = (Option(e.getMessage).toSeq ++ causeMessage.toSeq).mkString(": ")
    val hints = e match {
      case _: Key.MalformedValue if message.contains(ErrorMessages.inlineCredentialsError) =>
        Seq(s"Config file: $configPath")
      case _: Key.MalformedValue | _: ConfigDb.ConfigDbFormatError =>
        Nil
      case _ =>
        // ConfigDb.ConfigDbPermissionsError is private
        configDirPermissionsHint(configPath).toSeq
    }
    new ConfigError((message +: hints).mkString(System.lineSeparator()))
  }

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
        db.get(cred).fold(e => throw e, identity).map { value =>
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
        db.get(cred).fold(e => throw e, identity).map { value =>
          value.map { cred =>
            cred
              .copy(password = cred.password.map(_ => PasswordOption.Value(Secret(mask))))
              .asString
          }
        }
      case _ =>
        val valueOpt = db.getAsString(entry).fold(e => throw e, identity)
        if (entry.isPasswordOption && !showSecrets)
          valueOpt.map { values =>
            values.map { v =>
              PasswordOption.parse(v).fold(_ => mask, maskedPasswordOption)
            }
          }
        else
          valueOpt
    }

  def run(options: ConfigOptions, args: RemainingArgs): Unit =
    try run0(options, args)
    catch {
      case e: ConfigError =>
        System.err.println(s"Error: ${e.getMessage}")
        sys.exit(1)
      case e: AccessDeniedException =>
        System.err.println(s"Error: permission denied: ${e.getFile}")
        sys.exit(1)
    }

  private def run0(options: ConfigOptions, args: RemainingArgs): Unit = {

    val configPath = options.configFile
      .filter(_.trim.nonEmpty)
      .map(Paths.get(_))
      .getOrElse(CoursierPaths.scalaConfigFile())
      .toAbsolutePath

    def orFail[T](either: Either[Exception, T]): T =
      either.fold(e => throw configError(e, configPath), identity)

    if (options.dump)
      if (Files.exists(configPath)) {
        val content = Files.readAllBytes(configPath)
        System.out.write(content)
      }
      else
        throw new ConfigError(s"$configPath not found")
    else {
      val db = orFail(ConfigDb.open(configPath))

      args.all match {
        case Seq() =>
          throw new ConfigError(
            s"no key passed (usage: $progName config key [value…], " +
              s"run '$progName config --help' to list keys)"
          )
        case Seq(name, values @ _*) =>
          val keysMap = ConfigOptions.keys.map(e => e.fullName -> e).toMap
          keysMap.get(name) match {
            case None =>
              throw new ConfigError(
                s"unrecognized key $name (run '$progName config --help' to list keys)"
              )
            case Some(entry) =>
              if (values.isEmpty)
                if (options.unset) {
                  db.remove(entry)
                  orFail(db.save(configPath))
                }
                else {
                  val valueOpt =
                    try valueLines(db, entry, showSecrets = options.password)
                    catch {
                      case e: ConfigDb.ConfigDbFormatError =>
                        throw configError(e, configPath)
                    }
                  valueOpt match {
                    case Some(value) =>
                      for (v <- value)
                        if (options.password && entry.isPasswordOption)
                          PasswordOption.parse(v) match {
                            case Left(err) =>
                              throw new ConfigError(err)
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
              else if (options.unset)
                throw new ConfigError("--unset doesn't accept values, only a key")
              else {
                val finalValues =
                  if (options.passwordValue && entry.isPasswordOption)
                    values.map { input =>
                      PasswordOption.parse(input) match {
                        case Left(err) =>
                          throw new ConfigError(err)
                        case Right(passwordOption) =>
                          PasswordOption.Value(passwordOption.get()).asString.value
                      }
                    }
                  else
                    values

                orFail(db.setFromString(entry, finalValues))
                orFail(db.save(configPath))
              }
          }
      }
    }
  }

  private def progName: String =
    coursier.cli.Coursier.progName
}
