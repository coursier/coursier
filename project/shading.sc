import $ivy.`com.eed3si9n.jarjarabrams::jarjar-abrams-core:0.3.0`

import com.eed3si9n.jarjarabrams.{ShadePattern, Shader}
import coursier.util.{Gather, Task}
import mill._, mill.scalalib._

import java.io._
import java.util.zip._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

trait Shading extends JavaModule with PublishModule {

  // TODO Change that to shadedModules
  def shadedDependencies: T[Agg[Dep]]
  def validNamespaces: T[Seq[String]]
  def shadeRenames: T[Seq[(String, String)]]

  def shadedJars = T {
    val depToDependency = resolveCoursierDependency().apply(_)
    val depSeq          = transitiveIvyDeps()
    val (_, resolution) = mill.modules.Jvm.resolveDependenciesMetadata(
      repositoriesTask(),
      deps = depSeq.map(depToDependency),
      force = depSeq.filter(_.force).map(depToDependency),
      mapDependencies = Some(mapDependencies()),
      customizer = resolutionCustomizer(),
      ctx = Some(implicitly[mill.api.Ctx.Log])
    )
    val types = Set(
      coursier.Type.jar,
      coursier.Type.testJar,
      coursier.Type.bundle,
      coursier.Type("orbit"),
      coursier.Type("eclipse-plugin"),
      coursier.Type("maven-plugin")
    )

    def load(resolution: coursier.Resolution) = {
      val artifacts = resolution.artifacts(types = types)
      val loadedArtifacts = Gather[Task].gather(
        for (a <- artifacts)
          yield coursier.cache.Cache.default.file(a).run.map(a.optional -> _)
      ).unsafeRun

      val errors = loadedArtifacts.collect {
        case (false, Left(x))               => x
        case (true, Left(x)) if !x.notFound => x
      }
      if (errors.nonEmpty)
        sys.error(errors.toString)
      loadedArtifacts.collect { case (_, Right(x)) => x }
    }

    val shadedDepSeq = shadedDependencies()

    val allJars = load(resolution)
    val retainedJars =
      load(resolution.subset((depSeq.toSeq.filterNot(shadedDepSeq.toSet).map(depToDependency))))

    val shadedJars = allJars.filterNot(retainedJars.toSet)
    println(s"${shadedJars.length} JAR(s) to shade")
    for (j <- shadedJars)
      println(s"  $j")

    shadedJars.map(os.Path(_)).map(PathRef(_))
  }

  def jar = T {

    val shadeRules0 = {
      val renames = shadeRenames()
      if (renames.isEmpty) Nil
      else Seq(ShadePattern.Rename(renames.toList).inAll)
    }
    val orig        = super.jar().path
    val updated     = T.dest / (orig.last.stripSuffix(".jar") + "-shaded.jar")
    val shadedJars0 = shadedJars().map(_.path)

    val shader = Shader.bytecodeShader(shadeRules0, verbose = false)

    val inputFiles = Seq(orig) ++ shadedJars0

    var fos: OutputStream    = null
    var zos: ZipOutputStream = null
    try {
      fos = new FileOutputStream(updated.toIO)
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

    PathRef(updated)
  }

  def publishXmlDeps = T.task {
    val convert = resolvePublishDependency().apply(_)
    val orig    = super.publishXmlDeps()
    val shaded  = shadedDependencies().iterator.map(convert).toSet
    Agg(orig.toSeq.filterNot(shaded): _*)
  }
}
