package coursier.jvm

import java.io.File

trait JvmCacheLogger {
  def extracting(id: String, origin: String, dest: File): Unit
  def extracted(id: String, origin: String, dest: File): Unit
  def extractionFailed(id: String, origin: String, dest: File, error: Throwable): Unit
}

object JvmCacheLogger {

  private lazy val nop0 =
    new JvmCacheLogger {
      def extracting(id: String, origin: String, dest: File): Unit = {}
      def extracted(id: String, origin: String, dest: File): Unit = {}
      def extractionFailed(id: String, origin: String, dest: File, error: Throwable): Unit = {}
    }

  def nop: JvmCacheLogger =
    nop0
}
