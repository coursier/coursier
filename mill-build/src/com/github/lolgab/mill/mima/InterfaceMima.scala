// in the mill-mima package, so that we can override the package private
// `resolvedMimaPreviousArtifacts` below
package com.github.lolgab.mill.mima

import mill.*
import mill.api.*
import mill.javalib.Dep

import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Binary compatibility checking for the interface JAR.
  *
  * The classes it shades in are stripped from both the current and the previous artifacts, so that
  * only the `coursierapi` API surface is compared.
  */
trait InterfaceMima extends Mima {
  def resolvedMimaPreviousArtifacts: T[Seq[(Dep, PathRef)]] = Task {
    super.resolvedMimaPreviousArtifacts().zipWithIndex.map {
      case ((dep, ref), idx) =>
        val dest = Task.dest / s"$idx.jar"
        InterfaceMima.stripNonApiClasses(ref.path, dest)
        (dep, PathRef(dest))
    }
  }
}

object InterfaceMima {
  def stripNonApiClasses(input: os.Path, output: os.Path): Unit =
    Using.resources(
      new ZipFile(input.toIO),
      os.write.outputStream(output)
    ) { (zf, os0) =>
      val zos = new ZipOutputStream(os0)
      def keep(name: String) =
        !name.startsWith("coursierapi/shaded/") &&
        !name.startsWith("coursierapi/internal/")
      for (ent <- zf.entries().asScala if keep(ent.getName)) {
        zos.putNextEntry(new ZipEntry(ent))
        zos.write(zf.getInputStream(ent).readAllBytes())
        zos.closeEntry()
      }
      zos.finish()
    }
}
