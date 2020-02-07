package coursier.jvm

import java.io.File
import java.nio.charset.Charset

import coursier.cache.{Cache, CacheLogger}
import coursier.cache.internal.FileUtil
import coursier.util.Task
import dataclass.data

@data class JavaHome(
  cache: Option[JvmCache] = None,
  installIfNeeded: Boolean = true
) {

  def withCache(cache: JvmCache): JavaHome =
    withCache(Some(cache))

  def withJvmCacheLogger(logger: JvmCacheLogger): JavaHome =
    withCache(
      cache.map(_.withDefaultLogger(logger))
    )
  def withCoursierCache(cache: Cache[Task]): JavaHome =
    withCache(
      this.cache.map(_.withCache(cache))
    )

  def withDefaultCache: Task[JavaHome] =
    JvmCache.default.map(withCache(_))


  def default(): Task[File] =
    get(JavaHome.defaultId)

  def system(): Task[Option[File]] =
    Task.delay(Option(System.getenv("JAVA_HOME"))).flatMap {
      case None =>
        if (JvmCache.isMacOs)
          Task.delay {
            import sys.process._
            // FIXME What happens if no JDK is installed?
            Some(Seq("/usr/libexec/java_home").!!.trim)
              .filter(_.nonEmpty)
              .map(new File(_))
          }
        else
          Task.delay {
            val b = new ProcessBuilder("java", "-XshowSettings:properties", "-version")
            b.redirectInput(ProcessBuilder.Redirect.INHERIT)
            b.redirectOutput(ProcessBuilder.Redirect.PIPE)
            b.redirectError(ProcessBuilder.Redirect.PIPE)
            b.redirectErrorStream(true)
            val p = b.start()
            val output = new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
            val retCode = p.waitFor()
            if (retCode == 0) {
              val it = output
                .linesIterator
                .map(_.trim)
                .filter(_.startsWith("java.home = "))
                .map(_.stripPrefix("java.home = "))
              if (it.hasNext)
                Some(it.next())
                  .map(new File(_))
              else
                None
            } else
              None
          }
      case Some(home) =>
        Task.point(Some(new File(home)))
    }

  def get(id: String): Task[File] =
    getWithRetainedId(id)
      .map(_._2)

  def getWithRetainedId(id: String): Task[(String, File)] =
    if (id == JavaHome.systemId)
      system().flatMap {
        case None => Task.fail(new Exception("No system JVM found"))
        case Some(dir) => Task.point(JavaHome.systemId -> dir)
      }
    else if (id.startsWith(JavaHome.systemId + "|"))
      system().flatMap {
        case None => getWithRetainedId(id.stripPrefix(JavaHome.systemId + "|"))
        case Some(dir) => Task.point(JavaHome.systemId -> dir)
      }
    else {
      val id0 =
        if (id == JavaHome.defaultId)
          JavaHome.defaultJvm
        else
          id

      cache match {
        case None => Task.fail(new Exception("No JVM cache passed"))
        case Some(cache0) =>
          cache0.get(id0).map(id -> _)
      }
    }

}

object JavaHome {

  def default: Task[JavaHome] =
    JavaHome().withDefaultCache

  def systemId: String =
    "system"
  def defaultJvm: String =
    "adopt@1.8+"
  def defaultId: String =
    s"$systemId|$defaultJvm"

}
