package coursier.bootstrap

import java.io.{ByteArrayOutputStream, File, FileInputStream, OutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.jar.{Attributes => JarAttributes, JarFile, JarOutputStream, Manifest}
import java.util.regex.Pattern
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.bootstrap.util.{FileUtil, Zip}

import scala.collection.mutable

object Assembly {

  sealed abstract class Rule extends Product with Serializable

  object Rule {
    sealed abstract class PathRule extends Rule {
      def path: String
    }

    final case class Exclude(path: String) extends PathRule
    final case class ExcludePattern(path: Pattern) extends Rule

    object ExcludePattern {
      def apply(s: String): ExcludePattern =
        ExcludePattern(Pattern.compile(s))
    }

    // TODO Accept a separator: Array[Byte] argument in these
    // (to separate content with a line return in particular)
    final case class Append(path: String) extends PathRule
    final case class AppendPattern(path: Pattern) extends Rule

    object AppendPattern {
      def apply(s: String): AppendPattern =
        AppendPattern(Pattern.compile(s))
    }
  }

  val defaultRules = Seq(
    Assembly.Rule.Append("reference.conf"),
    Assembly.Rule.AppendPattern("META-INF/services/.*"),
    Assembly.Rule.Exclude("log4j.properties"),
    Assembly.Rule.Exclude(JarFile.MANIFEST_NAME),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
  )

  def make(
    jars: Seq[File],
    output: OutputStream,
    attributes: Seq[(JarAttributes.Name, String)],
    rules: Seq[Rule]
  ): Unit = {

    val rulesMap = rules.collect { case r: Rule.PathRule => r.path -> r }.toMap
    val excludePatterns = rules.collect { case Rule.ExcludePattern(p) => p }
    val appendPatterns = rules.collect { case Rule.AppendPattern(p) => p }

    val manifest = new Manifest
    manifest.getMainAttributes.put(JarAttributes.Name.MANIFEST_VERSION, "1.0")
    for ((k, v) <- attributes)
      manifest.getMainAttributes.put(k, v)

    var zos: ZipOutputStream = null

    try {
      zos = new JarOutputStream(output, manifest)

      val concatenedEntries = new mutable.HashMap[String, ::[(ZipEntry, Array[Byte])]]

      var ignore = Set.empty[String]

      for (jar <- jars) {
        var fis: FileInputStream = null
        var zis: ZipInputStream = null

        try {
          fis = new FileInputStream(jar)
          zis = new ZipInputStream(fis)

          for ((ent, content) <- Zip.zipEntries(zis)) {

            def append() =
              concatenedEntries += ent.getName -> ::((ent, content), concatenedEntries.getOrElse(ent.getName, Nil))

            rulesMap.get(ent.getName) match {
              case Some(Rule.Exclude(_)) =>
              // ignored

              case Some(Rule.Append(_)) =>
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
          if (zis != null)
            zis.close()
          if (fis != null)
            fis.close()
        }
      }

      for ((_, entries) <- concatenedEntries) {
        val (ent, _) = entries.head

        ent.setCompressedSize(-1L)

        if (entries.tail.nonEmpty)
          ent.setSize(entries.map(_._2.length).sum)

        zos.putNextEntry(ent)
        // for ((_, b) <- entries.reverse)
        //  zos.write(b)
        zos.write(entries.reverse.toArray.flatMap(_._2))
        zos.closeEntry()
      }
    } finally {
      if (zos != null)
        zos.close()
    }
  }

  def create(
    files: Seq[File],
    javaOpts: Seq[String],
    mainClass: String,
    output: Path,
    rules: Seq[Rule] = defaultRules,
    withPreamble: Boolean = true,
    disableJarChecking: Boolean = false
  ): Unit = {

    val attrs = Seq(
      JarAttributes.Name.MAIN_CLASS -> mainClass
    )

    val buffer = new ByteArrayOutputStream

    if (withPreamble)
      buffer.write(
        Preamble.shellPreamble(javaOpts, disableJarChecking).getBytes(UTF_8)
      )

    Assembly.make(files, buffer, attrs, rules)

    Files.write(output, buffer.toByteArray)
    FileUtil.tryMakeExecutable(output)
  }


}
