package coursier.launcher

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileNotFoundException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{CRC32, ZipEntry, ZipException, ZipOutputStream}

import coursier.launcher.internal.{FileUtil, WrappedZipInputStream, Zip}

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
                val files =
                  resources.map(r =>
                    () => WrappedZipInputStream.create(new ByteArrayInputStream(r.content))
                  )

                AssemblyGenerator.writeEntries(files.map(Left(_)), zos, parameters.rules)

                val remaining = c.entries.collect { case u: ClassPathEntry.Url => u }
                if (remaining.isEmpty)
                  parameters.content.drop(1)
                else {
                  val c0 = c.withEntries(remaining)
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
        parameters.javaProperties,
        parameters.pythonJep,
        parameters.python,
        parameters.extraContent
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
    properties: Seq[(String, String)],
    pythonJep: Boolean,
    python: Boolean,
    extraContent: Map[String, Seq[ClassLoaderContent]]
  ): Unit = {

    val content0 = ClassLoaderContent.withUniqueFileNames(content)
    val extraContent0 = extraContent.toVector.map {
      case (name, content) =>
        (name, ClassLoaderContent.withUniqueFileNames(content))
    }

    val bootstrapJar =
      FileUtil.readFully {
        val is =
          Thread.currentThread().getContextClassLoader.getResourceAsStream(bootstrapResourcePath)
        if (is == null) {
          val is0 =
            BootstrapGenerator.getClass.getClassLoader.getResourceAsStream(bootstrapResourcePath)
          if (is0 == null)
            throw new FileNotFoundException(s"Resource $bootstrapResourcePath")
          else
            is0
        }
        else
          is
      }

    val bootstrapZip = WrappedZipInputStream.create(new ByteArrayInputStream(bootstrapJar))

    for ((ent, content) <- extraZipEntries) {
      try outputZip.putNextEntry(ent)
      catch {
        case _: ZipException if ent.isDirectory =>
        // likely a duplicate entry error, ignoring it for directories
      }
      outputZip.write(content)
      outputZip.closeEntry()
    }

    for ((ent, data) <- bootstrapZip.entriesWithData()) {
      val writeData =
        try {
          outputZip.putNextEntry(ent)
          true
        }
        catch {
          case _: ZipException if ent.isDirectory =>
            // likely a duplicate entry error, ignoring it for directories
            false
          case e: ZipException if e.getMessage.startsWith("duplicate entry") =>
            // bootstrap entry already in user entries, assuming the user entry will work fine
            false
        }
      if (writeData)
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

    def putBinaryEntry(
      name: String,
      lastModified: Long,
      b: Array[Byte],
      compressed: Boolean = true
    ): Unit = {
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

    val allContent = Seq("bootstrap" -> content0) ++ extraContent0
    for ((name, content0) <- allContent) {
      val len = content0.length
      for ((c, idx) <- content0.zipWithIndex) {

        val urls = c.entries.collect {
          case u: ClassPathEntry.Url =>
            u.url
        }
        val resources = c.entries.collect {
          case r: ClassPathEntry.Resource =>
            r.fileName
        }

        val suffix = if (idx == len - 1) "" else "-" + (idx + 1)

        // really needed to sort here?
        putStringEntry(resourceDir + s"$name-jar-urls" + suffix, urls.mkString("\n"))
        putStringEntry(
          resourceDir + s"$name-jar-resources" + suffix,
          resources.mkString("\n")
        )

        if (c.loaderName.nonEmpty)
          putStringEntry(resourceDir + s"$name-loader-name" + suffix, c.loaderName)
      }

      val nameDir =
        if (name == "bootstrap") ""
        else name + "/"
      for (e <- content0.flatMap(_.entries).collect { case e: ClassPathEntry.Resource => e })
        putBinaryEntry(
          // FIXME Use name here too
          s"${resourceDir}jars/$nameDir${e.fileName}",
          e.lastModified,
          e.content,
          compressed = false
        )
    }

    val propFileContent =
      (("bootstrap.mainClass" -> mainClass) +: properties)
        .map {
          case (k, v) =>
            assert(!v.contains("\n"), s"Invalid ${"\\n"} character in property $k")
            s"$k=$v"
        }
        .mkString("\n")
    putStringEntry(resourceDir + "bootstrap.properties", propFileContent)

    if (pythonJep)
      putBinaryEntry(resourceDir + "set-python-jep-properties", time, Array.emptyByteArray)
    if (python)
      putBinaryEntry(resourceDir + "set-python-properties", time, Array.emptyByteArray)

    outputZip.closeEntry()
  }

  def resourceDir: String = "coursier/bootstrap/launcher/"

  private def bootstrapResourcePath(hasResources: Boolean, proguarded: Boolean) =
    (hasResources, proguarded) match {
      case (true, true) =>
        "bootstrap-resources.jar"
      case (true, false) =>
        "bootstrap-resources-orig.jar"
      case (false, true) =>
        "bootstrap.jar"
      case (false, false) =>
        "bootstrap-orig.jar"
    }

}
