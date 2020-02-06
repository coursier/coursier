package coursier.jvm

import org.codehaus.plexus.archiver.UnArchiver
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver
import org.codehaus.plexus.archiver.zip.ZipUnArchiver
import org.codehaus.plexus.logging.console.ConsoleLoggerManager
import org.codehaus.plexus.logging.{AbstractLogger, Logger}

sealed abstract class ArchiveType extends Product with Serializable {
  def unArchiver(): UnArchiver
}

object ArchiveType {

  private def nopLogger(): Logger =
    new AbstractLogger(Logger.LEVEL_DISABLED, "foo") {
      override def debug(message: String, throwable: Throwable) = ()
      override def info(message: String, throwable: Throwable) = ()
      override def warn(message: String, throwable: Throwable) = ()
      override def error(message: String, throwable: Throwable) = ()
      override def fatalError(message: String, throwable: Throwable) = ()
      override def getChildLogger(name: String) = this
    }

  case object Zip extends ArchiveType {
    def unArchiver(): UnArchiver = {
      val u = new ZipUnArchiver
      u.enableLogging(nopLogger())
      u
    }
  }
  case object Tgz extends ArchiveType {
    def unArchiver(): UnArchiver = {
      val u = new TarGZipUnArchiver
      u.enableLogging(nopLogger())
      u
    }
  }

  def parse(input: String): Option[ArchiveType] =
    input match {
      case "zip" => Some(Zip)
      case "tgz" => Some(Tgz)
      case _ => None
    }
}
