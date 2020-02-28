package coursier.launcher

import java.io.{ByteArrayOutputStream, OutputStream}
import java.nio.file.{Files, Path}
import java.util.jar.{Attributes, JarOutputStream, Manifest}

import coursier.launcher.internal.FileUtil

object ManifestJarGenerator extends Generator[Parameters.ManifestJar] {

  def generate(parameters: Parameters.ManifestJar, output: Path): Unit = {

    val cp = parameters.classpath.map(_.toURI.getRawPath).mkString(" ")

    val manifest = new Manifest
    val attr = manifest.getMainAttributes
    attr.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    attr.put(Attributes.Name.CLASS_PATH, cp)
    attr.put(Attributes.Name.MAIN_CLASS, parameters.mainClass)

    val content = {
      val baos = new ByteArrayOutputStream
      val jos = new JarOutputStream(baos, manifest)
      jos.close()
      baos.close()
      baos.toByteArray()
    }

    FileUtil.withOutputStream(output) { os =>

      for (p <- parameters.preambleOpt.map(_.value))
        os.write(p)

      os.write(content)
    }

    FileUtil.tryMakeExecutable(output)
  }
}
