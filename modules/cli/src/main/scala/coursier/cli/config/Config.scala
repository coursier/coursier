package coursier.cli.config

import caseapp.core.RemainingArgs
import caseapp.core.app.Command
import coursier.cache.ArchiveCache
import coursier.paths.CoursierPaths

import java.nio.file.{Files, Paths}
import java.util.Base64

import scala.cli.config.{ConfigDb, Key, Keys, PasswordOption}

object Config extends Command[ConfigOptions] {
  override def hidden = true

  val repositoriesMirrors = new Key.StringListEntry(Seq("repositories"), "mirrors")

  def extraKeys: Map[String, Key[_]] =
    Seq(repositoriesMirrors)
      .map(k => k.fullName -> k)
      .toMap

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
            Seq(Keys.repositoriesMirrors, Keys.defaultRepositories).map(e => e.fullName -> e)
          keysMap.get(name) match {
            case None => unrecognizedKey(name)
            case Some(entry) =>
              if (values.isEmpty)
                if (options.unset) {
                  db.remove(entry)
                  db.save(configPath).fold(e => throw new Exception(e), identity)
                }
                else {
                  val valueOpt = db.getAsString(entry).fold(e => throw new Exception(e), identity)
                  valueOpt match {
                    case Some(value) =>
                      for (v <- value)
                        if (options.password && entry.isPasswordOption)
                          PasswordOption.parse(v) match {
                            case Left(err) =>
                              System.err.println(err)
                              sys.exit(1)
                            case Right(passwordOption) =>
                              val password = passwordOption.getBytes()
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
