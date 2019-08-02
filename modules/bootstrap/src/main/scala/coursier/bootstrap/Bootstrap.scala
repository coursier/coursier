package coursier.bootstrap

import java.io._
import java.lang.{Boolean => JBoolean}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{CRC32, ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.bootstrap.util.{FileUtil, Zip}

object Bootstrap {

  def resourceDir: String = "coursier/bootstrap/launcher/"

  def resourcePath(name: String): String =
    s"${resourceDir}jars/$name"

  // only there for bin compat…
  def writeZip(
    output: OutputStream,
    content: Seq[ClassLoaderContent],
    mainClass: String,
    bootstrapResourcePath: String,
    deterministic: Boolean,
    extraZipEntries: Seq[(ZipEntry, Array[Byte])],
    properties: Seq[(String, String)]
  ): Unit = {

    val outputZip = new ZipOutputStream(output)

    writeZip(
      outputZip,
      content,
      mainClass,
      bootstrapResourcePath,
      deterministic,
      extraZipEntries,
      properties
    )

    outputZip.close()
  }

  def writeZip(
    outputZip: ZipOutputStream,
    content: Seq[ClassLoaderContent],
    mainClass: String,
    bootstrapResourcePath: String,
    deterministic: Boolean,
    extraZipEntries: Seq[(ZipEntry, Array[Byte])],
    properties: Seq[(String, String)]
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

    for ((ent, content) <- extraZipEntries) {
      outputZip.putNextEntry(ent)
      outputZip.write(content)
      outputZip.closeEntry()
    }

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

    def putBinaryEntry(name: String, lastModified: Long, b: Array[Byte], compressed: Boolean = true): Unit = {
      val entry = new ZipEntry(name)
      entry.setTime(lastModified)
      entry.setSize(b.length)
      if (!compressed) {
        // entry.setCompressedSize(b.length)
        val crc32 = new CRC32
        crc32.update(b)
        entry.setCrc(crc32.getValue)
        entry.setMethod(ZipEntry.STORED)
      }

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
      putBinaryEntry(resourcePath(e.fileName), e.lastModified, e.content, compressed = false)

    val propFileContent =
      (("bootstrap.mainClass" -> mainClass) +: properties)
        .map {
          case (k, v) =>
            assert(!v.contains("\n"), s"Invalid ${"\\n"} character in property $k")
            s"$k=$v"
        }
        .mkString("\n")
    putStringEntry(resourceDir + "bootstrap.properties", propFileContent)

    outputZip.closeEntry()
  }

  def proguardedBootstrapResourcePath: String = "bootstrap.jar"
  def bootstrapResourcePath: String = "bootstrap-orig.jar"
  def proguardedResourcesBootstrapResourcePath: String = "bootstrap-resources.jar"
  def resourcesBootstrapResourcePath: String = "bootstrap-resources-orig.jar"

  private lazy val proguardedResourcesBootstrapFound = {
    // caching in spite of Thread.currentThread().getContextClassLoader that may change…
    Thread.currentThread().getContextClassLoader.getResourceAsStream(proguardedResourcesBootstrapResourcePath) != null
  }

  def defaultDisableJarChecking(content: Seq[ClassLoaderContent]): Boolean =
    content.exists(_.entries.exists {
      case _: ClasspathEntry.Resource => true
      case _ => false
    })

  def create(
    content: Seq[ClassLoaderContent],
    mainClass: String,
    output: Path,
    javaOpts: Seq[String] = Nil,
    javaProperties: Seq[(String, String)] = Nil,
    bootstrapResourcePathOpt: Option[String] = None,
    deterministic: Boolean = false,
    withPreamble: Boolean = true,
    proguarded: Boolean = true,
    disableJarChecking: JBoolean = null,
    hybridAssembly: Boolean = false,
    extraZipEntries: Seq[(ZipEntry, Array[Byte])] = Nil
  ): Unit = {

    val disableJarChecking0 = Option(disableJarChecking)
      .fold(defaultDisableJarChecking(content))(x => x)

    val bootstrapResourcePath = bootstrapResourcePathOpt.getOrElse {

      val hasResources = content.exists { c =>
        c.entries.exists {
          case _: ClasspathEntry.Resource => true
          case _ => false
        }
      }

      (hasResources, proguarded) match {
        case (true, true) =>
          // first one may not have been packaged if coursier was built with JDK 11
          if (proguardedResourcesBootstrapFound)
            proguardedResourcesBootstrapResourcePath
          else
            resourcesBootstrapResourcePath
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
        Preamble.shellPreamble(javaOpts, disableJarChecking0).getBytes(UTF_8)
      )

    val zos = new ZipOutputStream(buffer)

    val content0 =
      if (hybridAssembly)
        content.headOption match {
          case None =>
            // shouldn't happen
            content
          case Some(c) =>
            val resources = c.entries.collect { case r: ClasspathEntry.Resource => r }

            if (resources.isEmpty)
              content
            else {
              val files = resources.map(r => () => new ZipInputStream(new ByteArrayInputStream(r.content)))

              Assembly.writeTo(
                files.map(Left(_)),
                zos,
                Assembly.defaultRules,
                Nil
              )

              val remaining = c.entries.collect { case u: ClasspathEntry.Url => u }
              if (remaining.isEmpty)
                content.drop(1)
              else {
                val c0 = c.copy(entries = remaining)
                c0 +: content.drop(1)
              }
            }
        }
      else
        content

    writeZip(
      zos,
      content0,
      mainClass,
      bootstrapResourcePath,
      deterministic,
      extraZipEntries,
      javaProperties
    )

    zos.close()
    Files.write(output, buffer.toByteArray)
    FileUtil.tryMakeExecutable(output)
  }

}
