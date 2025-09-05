package coursierbuild

import com.eed3si9n.jarjarabrams.{ShadePattern, Shader}
import coursier.util.{Gather, Task => CsTask}
import mill._, mill.scalalib._
import mill.util.JarManifest

import java.io._
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.zip._

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Using

trait Shading extends JavaModule with PublishModule {

  // TODO Change that to shadedModules
  def shadedDependencies: T[Agg[Dep]]
  def validNamespaces: T[Seq[String]]
  def shadeRenames: T[Seq[(String, String)]]

  def shadedJars = Task {
    val depToDependency = (d: Dep) => bindDependency().apply(d).dep
    val resolution      = millResolver().resolution(Seq(coursierDependency))
    val types           = Set(
      coursier.Type.jar,
      coursier.Type.testJar,
      coursier.Type.bundle,
      coursier.Type("orbit"),
      coursier.Type("eclipse-plugin"),
      coursier.Type("maven-plugin")
    )

    def load(resolution: coursier.Resolution) = {
      val artifacts       = resolution.artifacts(types = types)
      val loadedArtifacts = Gather[CsTask].gather(
        for (a <- artifacts)
          yield coursier.cache.Cache.default.file(a).run.map(a.optional -> _)
      ).unsafeRun()

      val errors = loadedArtifacts.collect {
        case (false, Left(x))               => x
        case (true, Left(x)) if !x.notFound => x
      }
      if (errors.nonEmpty)
        sys.error(errors.toString)
      loadedArtifacts.collect { case (_, Right(x)) => x }
    }

    val shadedDepSeq = shadedDependencies().iterator.map(depToDependency).toVector

    val allJars = load(resolution)
    val subset  =
      moduleDepsChecked.map(_.coursierDependency) ++
        ivyDeps().map(depToDependency).toSeq.filterNot(
          shadedDepSeq.toSet
        )
    val subset0 = subset.map { dep =>
      shadedDepSeq.iterator.foldLeft(dep) { (dep0, shaded) =>
        dep0.addExclusion(shaded.module.organization, shaded.module.name)
      }
    }
    val retainedJars = load {
      resolution.subset0(subset0) match {
        case Left(err)  => throw new Exception(err)
        case Right(res) => res
      }
    }

    val shadedJars = allJars.filterNot(retainedJars.toSet)
    println(s"${shadedJars.length} JAR(s) to shade")
    for (j <- shadedJars)
      println(s"  $j")

    if (shadedJars.isEmpty)
      sys.error("Found no JARs to shade")

    shadedJars.map(os.Path(_)).map(PathRef(_))
  }

  def manifest: T[JarManifest] = Task {
    val isMultiRelease = shadedJars().map(_.path).exists { shadedJar =>
      Using.resource(new ZipFile(shadedJar.toIO)) { zf =>
        val ent = zf.getEntry("META-INF/MANIFEST.MF")
        ent != null && {
          val mf = new java.util.jar.Manifest(zf.getInputStream(ent))
          Option(mf.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE))
            .contains("true")
        }
      }
    }
    val baseManifest = super.manifest()
    if (isMultiRelease)
      baseManifest.add(Attributes.Name.MULTI_RELEASE.toString -> "true")
    else
      baseManifest
  }

  def jar = Task {

    val shadeRules0 = {
      val renames = shadeRenames()
      if (renames.isEmpty) Nil
      else Seq(ShadePattern.Rename(renames.toList).inAll)
    }
    val orig        = super.jar().path
    val updated     = Task.dest / (orig.last.stripSuffix(".jar") + "-shaded.jar")
    val shadedJars0 = shadedJars().map(_.path)

    val shader = Shader.bytecodeShader(shadeRules0, verbose = false, skipManifest = false)

    val inputFiles = Seq(orig) ++ shadedJars0

    val keepDirs = validNamespaces()
      .iterator
      .flatMap(_.split("/").inits)
      .filter(_.nonEmpty)
      .map(_.map(_ + "/").mkString)
      .toSet

    var fos: OutputStream    = null
    var zos: ZipOutputStream = null
    try {
      fos = Files.newOutputStream(updated.toNIO)
      zos = new ZipOutputStream(fos)

      var seen = Set.empty[String]
      for (f <- inputFiles) {
        var zf: ZipFile = null
        try {
          zf = new ZipFile(f.toIO)

          val buf = Array.ofDim[Byte](64 * 1024)
          for (ent <- zf.entries.asScala)
            if (ent.getName.endsWith("/"))
              for {
                (_, updatedName) <- shader(Array.emptyByteArray, ent.getName)
                if keepDirs(updatedName)
                if !seen(updatedName)
              } {
                seen += updatedName
                val updatedEnt = {
                  val ent0 = new ZipEntry(updatedName)
                  ent0.setTime(ent.getTime)
                  for (t <- Option(ent.getLastModifiedTime))
                    ent0.setLastModifiedTime(t)
                  for (t <- Option(ent.getLastAccessTime))
                    ent0.setLastAccessTime(t)
                  for (t <- Option(ent.getCreationTime))
                    ent0.setCreationTime(t)
                  // ent0.setExtra ?
                  ent0.setComment(ent.getComment)
                  ent0
                }
                zos.putNextEntry(updatedEnt)
              }
            else {
              val baos            = new ByteArrayOutputStream
              var is: InputStream = null
              try {
                is = zf.getInputStream(ent)
                var read = -1
                while ({
                  read = is.read(buf)
                  read >= 0
                })
                  if (read > 0)
                    baos.write(buf, 0, read)
              }
              finally if (is != null)
                  is.close()
              val bytes = baos.toByteArray
              for {
                (updatedBytes, updatedName) <- shader(bytes, ent.getName)
                if !seen(updatedName)
              } {
                seen += updatedName
                val updatedEnt = {
                  val ent0 = new ZipEntry(updatedName)
                  ent0.setTime(ent.getTime)
                  for (t <- Option(ent.getLastModifiedTime))
                    ent0.setLastModifiedTime(t)
                  for (t <- Option(ent.getLastAccessTime))
                    ent0.setLastAccessTime(t)
                  for (t <- Option(ent.getCreationTime))
                    ent0.setCreationTime(t)
                  ent0.setSize(updatedBytes.length)
                  ent0.setCompressedSize(-1L)
                  // ent0.setCrc(ent.getCrc)
                  // ent0.setMethod(ent.getMethod)
                  // ent0.setExtra ?
                  ent0.setComment(ent.getComment)
                  ent0
                }
                zos.putNextEntry(updatedEnt)
                zos.write(updatedBytes, 0, updatedBytes.length)
              }
            }
        }
        finally if (zf != null)
            zf.close()
      }

      zos.finish()
    }
    finally {
      if (zos != null)
        zos.close()
      if (fos != null)
        fos.close()
    }

    onlyNamespaces(validNamespaces(), updated.toIO)

    PathRef(updated)
  }

  def publishXmlDeps = Task.Anon {
    val convert = resolvePublishDependency().apply(_)
    val orig    = super.publishXmlDeps()
    val shaded  = shadedDependencies().iterator.map(convert).toSet
    Agg(orig.iterator.toSeq.filterNot(shaded): _*)
  }

  def onlyNamespaces(namespaces: Seq[String], jar: File): Unit = {
    val allowedPrefixes  = namespaces.map(_.replace('.', '/') + "/")
    val extraAllowedDirs = namespaces.iterator
      .flatMap { ns =>
        ns.split('.').inits.filter(_.nonEmpty).map(_.map(_ + "/").mkString)
      }
      .toSet
    val zf           = new ZipFile(jar)
    val unrecognized = zf.entries()
      .asScala
      .map(_.getName)
      .filter { n =>
        !n.startsWith("META-INF/") && allowedPrefixes.forall(!n.startsWith(_)) &&
        !extraAllowedDirs.contains(n) &&
        n != "reflect.properties" &&                 // scala-reflect adds that
        n != "scala-collection-compat.properties" && // collection-compat adds that
        !n.contains("/libzstd-jni-") // com.github.luben:zstd-jni stuff (pulled via plexus-archiver)
      }
      .toVector
      .sorted
    for (u <- unrecognized)
      System.err.println(s"Unrecognized: $u")
    assert(unrecognized.isEmpty)
  }
}
