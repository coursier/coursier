package coursier
package cli

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, FileOutputStream, IOException}
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.jar.{JarFile, Attributes => JarAttributes}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import caseapp._
import coursier.cli.options.BootstrapOptions
import coursier.cli.util.{Assembly, LauncherBat, Zip}
import coursier.internal.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object Bootstrap extends CaseApp[BootstrapOptions] {

  private def createNativeBootstrap(
    options: BootstrapOptions,
    helper: Helper,
    mainClass: String
  ): Unit = {

    val files = helper.fetch(
      sources = options.artifactOptions.sources,
      javadoc = options.artifactOptions.javadoc,
      default = options.artifactOptions.default0(options.options.common.classifier0),
      artifactTypes = options.artifactOptions.artifactTypes(options.options.common.classifier0)
    )

    val log: String => Unit =
      if (options.options.common.verbosityLevel >= 0)
        s => Console.err.println(s)
      else
        _ => ()

    val tmpDir = new File(options.options.target)

    try {
      coursier.extra.Native.create(
        mainClass,
        files,
        new File(options.options.output),
        tmpDir,
        log,
        verbosity = options.options.common.verbosityLevel
      )
    } finally {
      if (!options.options.keepTarget)
        coursier.extra.Native.deleteRecursive(tmpDir)
    }
  }

  private def createJarBootstrap(javaOpts: Seq[String], output: File, content: Array[Byte], withPreamble: Boolean): Unit =
    if (withPreamble)
      createJarBootstrapWithPreamble(javaOpts, output, content)
    else
      createSimpleJarBootstrap(output, content)

  private def createSimpleJarBootstrap(output: File, content: Array[Byte]): Unit =
    try Files.write(output.toPath, content)
    catch {
      case e: IOException =>
        throw new BootstrapException(
          s"Error while writing $output${Option(e.getMessage).fold("")(" (" + _ + ")")}",
          e
        )
    }

  private def createJarBootstrapWithPreamble(javaOpts: Seq[String], output: File, content: Array[Byte]): Unit = {

    val argsPartitioner =
      """|nargs=$#
         |
         |i=1; while [ "$i" -le $nargs ]; do
         |         eval arg=\${$i}
         |         case $arg in
         |             -J-*) set -- "$@" "${arg#-J}" ;;
         |         esac
         |         i=$((i + 1))
         |     done
         |
         |set -- "$@" -jar "$0"
         |
         |i=1; while [ "$i" -le $nargs ]; do
         |         eval arg=\${$i}
         |         case $arg in
         |             -J-*) ;;
         |             *) set -- "$@" "$arg" ;;
         |         esac
         |         i=$((i + 1))
         |     done
         |
         |shift "$nargs"
         |""".stripMargin

    val javaCmd = Seq("java") ++
      javaOpts
        // escaping possibly a bit loose :-|
        .map(s => "'" + s.replace("'", "\\'") + "'") ++
      Seq("\"$@\"")

    val shellPreamble = Seq(
      "#!/usr/bin/env sh",
      argsPartitioner,
      "exec " + javaCmd.mkString(" ")
    ).mkString("", "\n", "\n")

    try Files.write(output.toPath, shellPreamble.getBytes(UTF_8) ++ content)
    catch {
      case e: IOException =>
        throw new BootstrapException(
          s"Error while writing $output${Option(e.getMessage).fold("")(" (" + _ + ")")}",
          e
        )
    }

    try {
      val perms = Files.getPosixFilePermissions(output.toPath).asScala.toSet

      var newPerms = perms
      if (perms(PosixFilePermission.OWNER_READ))
        newPerms += PosixFilePermission.OWNER_EXECUTE
      if (perms(PosixFilePermission.GROUP_READ))
        newPerms += PosixFilePermission.GROUP_EXECUTE
      if (perms(PosixFilePermission.OTHERS_READ))
        newPerms += PosixFilePermission.OTHERS_EXECUTE

      if (newPerms != perms)
        Files.setPosixFilePermissions(
          output.toPath,
          newPerms.asJava
        )
    } catch {
      case _: UnsupportedOperationException =>
      // Ignored
      case e: IOException =>
        throw new BootstrapException(
          s"Error while making $output executable" +
            Option(e.getMessage).fold("")(" (" + _ + ")"),
          e
        )
    }
  }

  private def createOneJarLikeJarBootstrap(
    options: BootstrapOptions,
    helper: Helper,
    mainClass: String,
    javaOpts: Seq[String],
    urls: Seq[String],
    files: Seq[File],
    output: File
  ): Unit = {

    val bootstrapJar =
      Option(Thread.currentThread().getContextClassLoader.getResourceAsStream("bootstrap.jar")) match {
        case Some(is) => FileUtil.readFully(is)
        case None =>
          throw new BootstrapException(s"Error: bootstrap JAR not found")
      }

    val isolatedDeps = options.options.isolated.isolatedDeps(options.options.common.resolutionOptions.scalaVersion)

    val (done, isolatedArtifactFiles) =
      options.options.isolated.targets.foldLeft((Set.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
        case ((done, acc), target) =>

          // TODO Add non regression test checking that optional artifacts indeed land in the isolated loader URLs

          val m = helper.fetchMap(
            sources = options.artifactOptions.sources,
            javadoc = options.artifactOptions.javadoc,
            default = options.artifactOptions.default0(options.options.common.classifier0),
            artifactTypes = options.artifactOptions.artifactTypes(options.options.common.classifier0),
            subset = isolatedDeps.getOrElse(target, Seq.empty).toSet
          )

          val m0 = m.filterKeys(url => !done(url))
          val done0 = done ++ m0.keys

          val (subUrls, subFiles) =
            if (options.options.standalone)
              (Nil, m0.values.toSeq)
            else
              (m0.keys.toSeq, Nil)

          val updatedAcc = acc + (target -> (subUrls, subFiles))

          (done0, updatedAcc)
      }

    val isolatedUrls = isolatedArtifactFiles.map { case (k, (v, _)) => k -> v }
    val isolatedFiles = isolatedArtifactFiles.map { case (k, (_, v)) => k -> v }

    val buffer = new ByteArrayOutputStream

    val bootstrapZip = new ZipInputStream(new ByteArrayInputStream(bootstrapJar))
    val outputZip = new ZipOutputStream(buffer)

    for ((ent, data) <- Zip.zipEntries(bootstrapZip)) {
      outputZip.putNextEntry(ent)
      outputZip.write(data)
      outputZip.closeEntry()
    }


    val time = if(options.options.deterministic){
      0
    } else {
      System.currentTimeMillis()
    }


    def putStringEntry(name: String, content: String): Unit = {
      val entry = new ZipEntry(name)
      entry.setTime(time)

      outputZip.putNextEntry(entry)
      outputZip.write(content.getBytes(UTF_8))
      outputZip.closeEntry()
    }

    def putEntryFromFile(name: String, f: File): Unit = {
      val entry = new ZipEntry(name)
      entry.setTime(f.lastModified())

      outputZip.putNextEntry(entry)
      outputZip.write(FileUtil.readFully(new FileInputStream(f)))
      outputZip.closeEntry()
    }

    putStringEntry("bootstrap-jar-urls", urls.filterNot(done).mkString("\n"))

    val fileNames = uniqueNames(files)

    if (options.options.isolated.anyIsolatedDep) {
      putStringEntry("bootstrap-isolation-ids", options.options.isolated.targets.mkString("\n"))

      for (target <- options.options.isolated.targets) {
        val urls = isolatedUrls.getOrElse(target, Nil)
        val files = isolatedFiles.getOrElse(target, Nil)
        putStringEntry(s"bootstrap-isolation-$target-jar-urls", urls.mkString("\n"))
        putStringEntry(s"bootstrap-isolation-$target-jar-resources", fileNames.mkString("\n"))
      }
    }

    for ((file, name) <- files.zip(fileNames))
      putEntryFromFile(name, file)

    putStringEntry("bootstrap-jar-resources", fileNames.mkString("\n"))
    putStringEntry("bootstrap.properties", s"bootstrap.mainClass=$mainClass")

    outputZip.closeEntry()

    outputZip.close()

    createJarBootstrap(
      javaOpts,
      output,
      buffer.toByteArray,
      options.options.preamble
    )
  }

  private def uniqueNames(files: Seq[File]): Seq[String] = {
    val fileIndex = mutable.Map.empty[String, Int]
    def pathFor(f: File) = {
      val name = f.getName
      val index = fileIndex.getOrElse(name, 0)
      fileIndex(name) = index + 1
      val uniqueName =
        if (index == 0)
          name
        else
          name.stripSuffix(".jar") + s"-$index.jar"
      s"jars/$uniqueName"
    }
    files.map(pathFor)
  }

  private def defaultRules = Seq(
    Assembly.Rule.Append("reference.conf"),
    Assembly.Rule.AppendPattern("META-INF/services/.*"),
    Assembly.Rule.Exclude("log4j.properties"),
    Assembly.Rule.Exclude(JarFile.MANIFEST_NAME),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
  )

  private def createAssemblyJar(
    options: BootstrapOptions,
    files: Seq[File],
    javaOpts: Seq[String],
    mainClass: String,
    output: File
  ): Unit = {

    val parsedRules = options.options.rule.map { s =>
      s.split(":", 2) match {
        case Array("append", v) => Assembly.Rule.Append(v)
        case Array("append-pattern", v) => Assembly.Rule.AppendPattern(v)
        case Array("exclude", v) => Assembly.Rule.Exclude(v)
        case Array("exclude-pattern", v) => Assembly.Rule.ExcludePattern(v)
        case _ =>
          sys.error(s"Malformed assembly rule: $s")
      }
    }

    val rules =
      (if (options.options.defaultRules) defaultRules else Nil) ++ parsedRules

    val attrs = Seq(
      JarAttributes.Name.MAIN_CLASS -> mainClass
    )

    val baos = new ByteArrayOutputStream
    Assembly.make(files, baos, attrs, rules)

    createJarBootstrap(
      javaOpts,
      output,
      baos.toByteArray,
      options.options.preamble
    )
  }

  def run(options: BootstrapOptions, args: RemainingArgs): Unit = {
    try bootstrap(options, args)
    catch {
      case e: BootstrapException =>
        Console.err.println(e.message)
        sys.exit(1)
    }
  }

  def bootstrap(options: BootstrapOptions, args: RemainingArgs): Unit = {

    val helper = new Helper(
      options.options.common,
      args.all,
      isolated = options.options.isolated,
      warnBaseLoaderNotFound = false
    )

    val output0 = new File(options.options.output)
    if (!options.options.force && output0.exists())
      throw new BootstrapException(
        s"Error: ${options.options.output} already exists, use -f option to force erasing it."
      )

    val mainClass =
      if (options.options.mainClass.isEmpty)
        helper.retainedMainClass
      else
        options.options.mainClass

    if (options.options.native)
      createNativeBootstrap(options, helper, mainClass)
    else {

      val (validProperties, wrongProperties) = options.options.property.partition(_.contains("="))
      if (wrongProperties.nonEmpty)
        throw new BootstrapException(s"Wrong -P / --property option(s):\n${wrongProperties.mkString("\n")}")

      val properties0 = validProperties.map { s =>
        s.split("=", 2) match {
          case Array(k, v) => k -> v
          case _ => sys.error("Cannot possibly happen")
        }
      }

      val javaOpts = options.options.javaOpt ++
        properties0.map { case (k, v) => s"-D$k=$v" }

      val (urls, files) =
        helper.fetchMap(
          sources = options.artifactOptions.sources,
          javadoc = options.artifactOptions.javadoc,
          default = options.artifactOptions.default0(options.options.common.classifier0),
          artifactTypes = options.artifactOptions.artifactTypes(options.options.common.classifier0)
        ).toList.foldLeft((List.empty[String], List.empty[File])){
          case ((urls, files), (url, file)) =>
            if (options.options.assembly || options.options.standalone) (urls, file :: files)
            else if (options.options.embedFiles && url.startsWith("file:/")) (urls, file :: files)
            else (url :: urls, files)
        }

      val generateBat = options.options.bat
        .getOrElse(LauncherBat.isWindows)

      val bat = new File(output0.getParentFile, s"${output0.getName}.bat")

      if (generateBat && !options.options.force && bat.exists())
        throw new BootstrapException(s"Error: $bat already exists, use -f option to force erasing it.")

      if (options.options.assembly)
        createAssemblyJar(options, files, javaOpts, mainClass, output0)
      else
        createOneJarLikeJarBootstrap(
          options,
          helper,
          mainClass,
          javaOpts,
          urls,
          files,
          output0
        )

      if (generateBat) {
        // no escaping for javaOpts :|
        val content = LauncherBat(javaOpts.mkString(" "))
        Files.write(bat.toPath, content.getBytes(Charset.defaultCharset()))
      }
    }
  }

  final class BootstrapException(val message: String, cause: Throwable = null) extends Exception(message, cause)

}
