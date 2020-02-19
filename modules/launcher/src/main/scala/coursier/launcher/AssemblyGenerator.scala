package coursier.launcher

import java.io.{File, OutputStream}
import java.nio.file.Path
import java.util.jar.{Attributes => JarAttributes, JarOutputStream}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}

import coursier.launcher.internal.{FileUtil, Zip}

import scala.collection.mutable

object AssemblyGenerator extends Generator[Parameters.Assembly] {

  def generate(parameters: Parameters.Assembly, output: Path): Unit = {

    FileUtil.withOutputStream(output) { os =>

      for (p <- parameters.preambleOpt.map(_.value))
        os.write(p)

      make(parameters.files, os, parameters.finalAttributes, parameters.rules, parameters.extraZipEntries)
    }

    FileUtil.tryMakeExecutable(output)
  }


  private def make(
    jars: Seq[File],
    output: OutputStream,
    attributes: Seq[(JarAttributes.Name, String)],
    rules: Seq[MergeRule],
    extraZipEntries: Seq[(ZipEntry, Array[Byte])] = Nil
  ): Unit = {

    val manifest = new java.util.jar.Manifest
    manifest.getMainAttributes.put(JarAttributes.Name.MANIFEST_VERSION, "1.0")
    for ((k, v) <- attributes)
      manifest.getMainAttributes.put(k, v)

    var zos: ZipOutputStream = null

    try {
      zos = new JarOutputStream(output, manifest)
      writeEntries(jars.map(Right(_)), zos, rules, extraZipEntries)
    } finally {
      if (zos != null)
        zos.close()
    }
  }

  def writeEntries(
    jars: Seq[Either[() => ZipInputStream, File]],
    zos: ZipOutputStream,
    rules: Seq[MergeRule]
  ): Unit =
    writeEntries(jars, zos, rules, Nil)

  private def writeEntries(
    jars: Seq[Either[() => ZipInputStream, File]],
    zos: ZipOutputStream,
    rules: Seq[MergeRule],
    extraZipEntries: Seq[(ZipEntry, Array[Byte])]
  ): Unit = {

    val rulesMap = rules.collect { case r: MergeRule.PathRule => r.path -> r }.toMap
    val excludePatterns = rules.collect { case e: MergeRule.ExcludePattern => e.path }
    val appendPatterns = rules.collect { case a: MergeRule.AppendPattern => a.path }

    for ((ent, content) <- extraZipEntries) {
      zos.putNextEntry(ent)
      zos.write(content)
      zos.closeEntry()
    }

    val concatenatedEntries = new mutable.HashMap[String, ::[(ZipEntry, Array[Byte])]]

    var ignore = Set.empty[String]

    for (jar <- jars) {
      var zif: ZipFile = null
      var zis: ZipInputStream = null

      try {
        val entries =
          jar match {
            case Left(f) =>
              zis = f()
              Zip.zipEntries(zis)
            case Right(f) =>
              zif = new ZipFile(f)
              Zip.zipEntries(zif)
          }

        for ((ent, content) <- entries) {

          def append(): Unit =
            concatenatedEntries += ent.getName -> ::((ent, content), concatenatedEntries.getOrElse(ent.getName, Nil))

          rulesMap.get(ent.getName) match {
            case Some(e: MergeRule.Exclude) =>
              // ignored

            case Some(a: MergeRule.Append) =>
              append()

            case None =>
              if (!excludePatterns.exists(_.matcher(ent.getName).matches())) {
                if (appendPatterns.exists(_.matcher(ent.getName).matches()))
                  append()
                else if (!ignore(ent.getName)) {
                  ent.setCompressedSize(-1L)
                  zos.putNextEntry(ent)
                  zos.write(content)
                  zos.closeEntry()

                  ignore += ent.getName
                }
              }
          }
        }

      } finally {
        if (zif != null)
          zif.close()
        if (zis != null)
          zis.close()
      }
    }

    for ((_, entries) <- concatenatedEntries) {
      val (ent, _) = entries.head

      ent.setCompressedSize(-1L)

      if (entries.tail.nonEmpty)
        ent.setSize(entries.map(_._2.length).sum)

      zos.putNextEntry(ent)
      zos.write(entries.reverse.toArray.flatMap(_._2))
      zos.closeEntry()
    }
  }

}
