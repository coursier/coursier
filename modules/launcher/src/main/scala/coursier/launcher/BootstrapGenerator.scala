package coursier.launcher

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileNotFoundException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{CRC32, ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.launcher.internal.{FileUtil, Zip}

object BootstrapGenerator extends Generator[Parameters.Bootstrap] {

  def generate(parameters: Parameters.Bootstrap, output: Path): Unit = {

    val bootstrapResourcePath0 = parameters.bootstrapResourcePathOpt.getOrElse {
      bootstrapResourcePath(parameters.hasResources, parameters.proguarded)
    }

    FileUtil.withOutputStream(output) { os =>

      for (p <- parameters.finalPreambleOpt.map(_.value))
        os.write(p)

      val zos = new ZipOutputStream(os)

      val content0 =
        if (parameters.hybridAssembly)
          parameters.content.headOption match {
            case None =>
              // shouldn't happen
              parameters.content
            case Some(c) =>
              val resources = c.entries.collect { case r: ClassPathEntry.Resource => r }

              if (resources.isEmpty)
                parameters.content
              else {
                val files = resources.map(r => () => new ZipInputStream(new ByteArrayInputStream(r.content)))

                AssemblyGenerator.writeEntries(files.map(Left(_)), zos, MergeRule.default)

                val remaining = c.entries.collect { case u: ClassPathEntry.Url => u }
                if (remaining.isEmpty)
                  parameters.content.drop(1)
                else {
                  val c0 = c.copy(entries = remaining)
                  c0 +: parameters.content.drop(1)
                }
              }
          }
        else
          parameters.content

      writeZip(
        zos,
        content0,
        parameters.mainClass,
        bootstrapResourcePath0,
        parameters.deterministic,
        parameters.extraZipEntries,
        parameters.javaProperties
      )

      zos.close()
    }

    FileUtil.tryMakeExecutable(output)
  }

  private def writeZip(
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
          throw new FileNotFoundException(s"Resource $bootstrapResourcePath")
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
      outputZip.write(content.getBytes(StandardCharsets.UTF_8))
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
        case ClassPathEntry.Url(url) =>
          url
      }
      val resources = c.entries.collect {
        case r: ClassPathEntry.Resource =>
          r.fileName
      }

      val suffix = if (idx == len - 1) "" else "-" + (idx + 1)

      // really needed to sort here?
      putStringEntry(resourceDir + "bootstrap-jar-urls" + suffix, urls.sorted.mkString("\n"))
      putStringEntry(resourceDir + "bootstrap-jar-resources" + suffix, resources.sorted.mkString("\n"))

      if (c.loaderName.nonEmpty)
        putStringEntry(resourceDir + "bootstrap-loader-name" + suffix, c.loaderName)
    }

    for (e <- content0.flatMap(_.entries).collect { case e: ClassPathEntry.Resource => e })
      putBinaryEntry(s"${resourceDir}jars/${e.fileName}", e.lastModified, e.content, compressed = false)

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


  def resourceDir: String = "coursier/bootstrap/launcher/"

  private lazy val proguardedResourcesBootstrapOpt = {
    val path = "bootstrap-resources.jar"
    // caching in spite of Thread.currentThread().getContextClassLoader that may change…
    val found = Thread.currentThread().getContextClassLoader.getResourceAsStream(path) != null
    if (found) Some(path)
    else None
  }

  private def bootstrapResourcePath(hasResources: Boolean, proguarded: Boolean) =
    (hasResources, proguarded) match {
      case (true, true) =>
        // first one may not have been packaged if coursier was built with JDK 11
        proguardedResourcesBootstrapOpt.getOrElse("bootstrap-resources-orig.jar")
      case (true, false) =>
        "bootstrap-resources-orig.jar"
      case (false, true) =>
        "bootstrap.jar"
      case (false, false) =>
        "bootstrap-orig.jar"
    }

}
