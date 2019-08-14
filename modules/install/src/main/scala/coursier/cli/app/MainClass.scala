package coursier.cli.app

import java.io.{File, InputStream}
import java.util.jar.{Manifest => JManifest}
import java.util.zip.ZipFile

import coursier.core.Dependency

object MainClass {

  private def manifestPath = "META-INF/MANIFEST.MF"

  def mainClasses(jars: Seq[File]): Map[(String, String), String] = {

    val metaInfs = jars.flatMap { f =>
      val zf = new ZipFile(f)
      val entryOpt = Option(zf.getEntry(manifestPath))
      entryOpt.map(e => () => zf.getInputStream(e)).toSeq
    }

    val mainClasses = metaInfs.flatMap { f =>
      var is: InputStream = null
      val attributes =
        try {
          is = f()
          new JManifest(is).getMainAttributes
        } finally {
          if (is != null)
            is.close()
        }

      def attributeOpt(name: String) =
        Option(attributes.getValue(name))

      val vendor = attributeOpt("Implementation-Vendor-Id").getOrElse("")
      val title = attributeOpt("Specification-Title").getOrElse("")
      val mainClass = attributeOpt("Main-Class")

      mainClass.map((vendor, title) -> _)
    }

    mainClasses.toMap
  }

  def retainedMainClassOpt(
    mainClasses: Map[(String, String), String],
    mainDependencyOpt: Option[Dependency]
  ): Option[String] =
    if (mainClasses.size == 1) {
      val (_, mainClass) = mainClasses.head
      Some(mainClass)
    } else {

      // Trying to get the main class of the first artifact
      val mainClassOpt = for {
        dep <- mainDependencyOpt
        module = dep.module
        mainClass <- mainClasses.collectFirst {
          case ((org, name), mainClass)
            if org == module.organization.value && (
              module.name.value == name ||
                module.name.value.startsWith(name + "_") // Ignore cross version suffix
              ) =>
            mainClass
        }
      } yield mainClass

      def sameOrgOnlyMainClassOpt = for {
        dep <- mainDependencyOpt
        module = dep.module
        orgMainClasses = mainClasses.collect {
          case ((org, _), mainClass)
            if org == module.organization.value =>
            mainClass
        }.toSet
        if orgMainClasses.size == 1
      } yield orgMainClasses.head

      mainClassOpt.orElse(sameOrgOnlyMainClassOpt)
    }

}
