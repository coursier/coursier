package coursier.bootstrap

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.bootstrap.util.{FileUtil, Zip}

object Bootstrap {

  def resourceDir: String = "coursier/bootstrap/launcher/"

  def resourcePath(name: String): String =
    s"${resourceDir}jars/$name"

  def writeZip(
    output: OutputStream,
    content: Seq[ClassLoaderContent],
    mainClass: String,
    bootstrapResourcePath: String,
    deterministic: Boolean
  ): Unit = {

    val content0 = ClassLoaderContent.withUniqueFileNames(content)

    val bootstrapJar =
      FileUtil.readFully {
        val is = Thread.currentThread().getContextClassLoader.getResourceAsStream(bootstrapResourcePath)
        if (is == null)
          throw new BootstrapException(s"Error: bootstrap JAR not found")
        is
      }

    val bootstrapZip = new ZipInputStream(new ByteArrayInputStream(bootstrapJar))
    val outputZip = new ZipOutputStream(output)

    for ((ent, data) <- Zip.zipEntries(bootstrapZip)) {
      outputZip.putNextEntry(ent)
      outputZip.write(data)
      outputZip.closeEntry()
    }

    val time =
      if (deterministic)
        0
      else
        System.currentTimeMillis()

    def putStringEntry(name: String, content: String): Unit = {
      val entry = new ZipEntry(name)
      entry.setTime(time)

      outputZip.putNextEntry(entry)
      outputZip.write(content.getBytes(UTF_8))
      outputZip.closeEntry()
    }

    def putBinaryEntry(name: String, lastModified: Long, b: Array[Byte]): Unit = {
      val entry = new ZipEntry(name)
      entry.setTime(lastModified)

      outputZip.putNextEntry(entry)
      outputZip.write(b)
      outputZip.closeEntry()
    }

    val len = content0.length
    for ((c, idx) <- content0.zipWithIndex) {

      val urls = c.entries.collect {
        case ClasspathEntry.Url(url) =>
          url
      }
      val resources = c.entries.collect {
        case r: ClasspathEntry.Resource =>
          r.fileName
      }

      val suffix = if (idx == len - 1) "" else "-" + (idx + 1)

      // really needed to sort here?
      putStringEntry(resourceDir + "bootstrap-jar-urls" + suffix, urls.sorted.mkString("\n"))
      putStringEntry(resourceDir + "bootstrap-jar-resources" + suffix, resources.sorted.mkString("\n"))

      if (c.loaderName.nonEmpty)
        putStringEntry(resourceDir + "bootstrap-loader-name" + suffix, c.loaderName)
    }

    for (e <- content0.flatMap(_.entries).collect { case e: ClasspathEntry.Resource => e })
      putBinaryEntry(resourcePath(e.fileName), e.lastModified, e.content)

    putStringEntry(resourceDir + "bootstrap.properties", s"bootstrap.mainClass=$mainClass")

    outputZip.closeEntry()

    outputZip.close()
  }

  def proguardedBootstrapResourcePath: String = "bootstrap.jar"
  def bootstrapResourcePath: String = "bootstrap-orig.jar"
  def proguardedResourcesBootstrapResourcePath: String = "bootstrap-resources.jar"
  def resourcesBootstrapResourcePath: String = "bootstrap-resources-orig.jar"

  def create(
    content: Seq[ClassLoaderContent],
    mainClass: String,
    output: Path,
    javaOpts: Seq[String] = Nil,
    bootstrapResourcePathOpt: Option[String] = None,
    deterministic: Boolean = false,
    withPreamble: Boolean = true,
    proguarded: Boolean = true
  ): Unit = {

    val bootstrapResourcePath = bootstrapResourcePathOpt.getOrElse {

      val hasResources = content.exists { c =>
        c.entries.exists {
          case _: ClasspathEntry.Resource => true
          case _ => false
        }
      }

      (hasResources, proguarded) match {
        case (true, true) =>
          proguardedResourcesBootstrapResourcePath
        case (true, false) =>
          resourcesBootstrapResourcePath
        case (false, true) =>
          proguardedBootstrapResourcePath
        case (false, false) =>
          Bootstrap.bootstrapResourcePath
      }
    }

    val buffer = new ByteArrayOutputStream

    if (withPreamble)
      buffer.write(
        Preamble.shellPreamble(javaOpts).getBytes(UTF_8)
      )

    writeZip(
      buffer,
      content,
      mainClass,
      bootstrapResourcePath,
      deterministic
    )

    Files.write(output, buffer.toByteArray)
    FileUtil.tryMakeExecutable(output)
  }

}
