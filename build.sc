//
// *** mill is only used to build the website for now ***
//

import $ivy.`org.jsoup:jsoup:1.10.3`

import java.nio.file._

import mill.scalalib.Lib.resolveDependencies

trait Mdoc extends Module {
  def enableJS: T[Boolean] =
    T(false)
  def module: T[mill.scalalib.Dep]
  def scalaVersion: T[String]
  def inputDir: T[PathRef]
  // running into issues making that a task (watch triggered in loop…)
  def outputDir: PathRef

  def mdocVersion = T("1.2.7")
  def mdocFullClasspath = T.sources {
    val sv = scalaVersion()
    val mv = mdocVersion()
    val extra =
      if (enableJS())
        Seq(mill.scalalib.Dep.parse(s"org.scalameta::mdoc-js:$mv"))
      else
        Nil
    resolveDependencies(
      Seq(coursier.Cache.ivy2Local, coursier.maven.MavenRepository("https://repo1.maven.org/maven2")),
      mill.scalalib.Lib.depToDependency(_, sv),
      Seq(
        mill.scalalib.Dep.parse(s"org.scalameta::mdoc:$mv"),
        module()
      ) ++ extra
    ).map(_.toSeq)
  }

  def mdocInput = T.sources {
    Seq(
      inputDir()
    )
  }

  def mdocProps = T {
    Map.empty[String, String]
  }

  def mdocCommand = T {
    val sv = scalaVersion()
    val v = version()
    val args = Seq(
      "--in", mdocInput().head.path.toNIO.toString,
      "--out", outputDir.path.toNIO.toString
    ) ++ mdocProps().toSeq.flatMap {
      case (k, v) =>
        Seq(s"--site.$k", v)
    }
    Seq("java", "-cp", mdocFullClasspath().map(_.path.toNIO.toString).mkString(java.io.File.pathSeparator), "mdoc.Main") ++ args
  }

  def mdoc = T.sources {
    mdocInput()
    val cmd = mdocCommand()
    val p = new ProcessBuilder(cmd: _*)
    p.inheritIO()
    val b = p.start()
    val retCode = b.waitFor()
    if (retCode != 0)
      sys.error(s"Error running ${cmd.mkString(" ")} (return code: $retCode)")
    Seq(outputDir)
  }

  def mdocWatch() = T.command {
    val cmd = mdocCommand() ++ Seq("--watch")
    val p = new ProcessBuilder(cmd: _*)
    p.inheritIO()
    val b = p.start()
    val retCode = b.waitFor()
    if (retCode != 0)
      sys.error(s"Error running ${cmd.mkString(" ")} (return code: $retCode)")
  }

}

trait Docusaurus extends Module {
  def beforeRun: T[_] = T(())
  def relativizeInput = T.sources {
    Seq(
      PathRef(os.Path(Paths.get("doc/website/build").toAbsolutePath))
    )
  }
  def postProcess = T {
    Relativize.htmlSite(relativizeInput().head.path.toNIO)
  }

  def websiteDir = T {
    PathRef(os.Path(Paths.get("doc/website").toAbsolutePath))
  }
  def packageJson = T.sources {
    Seq(
      PathRef(os.Path(websiteDir().path.toNIO.resolve("package.json").toAbsolutePath))
    )
  }
  def npmInstall = T {
    packageJson() // unused here, just to re-trigger this task if package.json changes

    val cmd = Seq("npm", "install")
    val p = new ProcessBuilder(cmd: _*)
    p.directory(websiteDir().path.toIO)
    p.inheritIO()
    val b = p.start()
    val retCode = b.waitFor()
    if (retCode != 0)
      sys.error(s"Error running ${cmd.mkString(" ")} (return code: $retCode)")
  }

  def httpServerBg() = T.command {
    npmInstall()
    val cmd = Seq("npx", "http-server", websiteDir().path.toNIO.resolve("build/coursier").toString)
    val p = new ProcessBuilder(cmd: _*)
    p.directory(websiteDir().path.toIO)
    p.inheritIO()
    val b = p.start()
  }

  def yarnRunBuild() = T.command {
    npmInstall()
    beforeRun()
    val cmd = Seq("yarn", "run", "build")
    val p = new ProcessBuilder(cmd: _*)
    p.directory(websiteDir().path.toIO)
    p.inheritIO()
    val b = p.start()
    val retCode = b.waitFor()
    if (retCode != 0)
      sys.error(s"Error running ${cmd.mkString(" ")} (return code: $retCode)")
  }

  def yarnStartBg() = T.command {
    npmInstall()
    beforeRun()
    val cmd = Seq("yarn", "run", "start")
    val p = new ProcessBuilder(cmd: _*)
    p.directory(websiteDir().path.toIO)
    p.inheritIO()
    val b = p.start()
  }
}

object doc extends Module { self =>
  def version = T("0.1.0-mdoc-SNAPSHOT")
  def actualVersion = T {
    sys.env.get("TRAVIS_TAG").fold(version())(_.stripPrefix("v"))
  }

  def scalaVersionFile = T.sources {
    Seq(PathRef(os.Path(Paths.get("project/ScalaVersion.scala").toAbsolutePath)))
  }
  def scalaVersion = T {
    val b = Files.readAllBytes(scalaVersionFile().head.path.toNIO)
    val s = new String(b, "UTF-8").linesIterator.toList.map(_.trim).filter(_.contains("def scala212")) match {
      case h :: Nil => h
      case _ => ???
    }
    val prefix = """def scala212 = """"
    val suffix = "\""
    assert(s.startsWith(prefix))
    assert(s.endsWith(suffix))
    s.stripPrefix(prefix).stripSuffix(suffix)
  }

  def publishLocal() = T.command {
    val cmd = Seq("sbt", "set version in ThisBuild := \"" + version() + "\"", "coreJVM/publishLocal", "cacheJVM/publishLocal")
    val p = new ProcessBuilder(cmd: _*)
    p.inheritIO()
    val b = p.start()
    val retCode = b.waitFor()
    if (retCode != 0)
      sys.error(s"Error running ${cmd.mkString(" ")} (return code: $retCode)")
  }

  object mdoc extends Mdoc {
    def module = T {
      val v = self.version()
      mill.scalalib.Dep.parse(s"io.get-coursier:coursier-cache_2.12:$v")
    }
    def scalaVersion = T(self.scalaVersion())
    def inputDir = T {
      PathRef(os.Path(Paths.get("doc/docs").toAbsolutePath))
    }
    def outputDir = {
      PathRef(os.Path(Paths.get("doc/processed-docs").toAbsolutePath))
    }
    def mdocProps = T {
      val v = self.actualVersion()
      val sv = scalaVersion()
      val extraSbt =
        if (v.endsWith("SNAPSHOT"))
          """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
        else
          ""
      super.mdocProps() ++ Map(
        "VERSION" -> v,
        "EXTRA_SBT" -> extraSbt,
        "PLUGIN_VERSION" -> v,
        "SCALA_VERSION" -> sv,
        "MILL_VERSION" -> sys.props("MILL_VERSION")
      )
    }
  }

  object docusaurus extends Docusaurus {
    def beforeRun: T[_] = T(mdoc.mdoc())
  }

}

object Relativize {
  // from https://github.com/olafurpg/sbt-docusaurus/blob/16e548280117d3fcd8db4c244f91f089470b8ee7/plugin/src/main/scala/sbtdocusaurus/internal/Relativize.scala

  import java.net.URI
  import java.nio.charset.Charset
  import java.nio.charset.StandardCharsets
  import java.nio.file.FileVisitResult
  import java.nio.file.Files
  import java.nio.file.Path
  import java.nio.file.Paths
  import java.nio.file.SimpleFileVisitor
  import java.nio.file.attribute.BasicFileAttributes
  import org.jsoup.Jsoup
  import org.jsoup.nodes.Element
  import scala.collection.JavaConverters._

  def htmlSite(site: Path): Unit = {
    Files.walkFileTree(
      site,
      new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (file.getFileName.toString.endsWith(".html")) {
            processHtmlFile(site, file)
          }
          super.visitFile(file, attrs)
        }
      }
    )
  }

  // actual host name doesn't matter
  private val baseUri = URI.create("http://example.com/")

  def processHtmlFile(site: Path, file: Path): Unit = {
    val originRelativeUri = relativeUri(site.relativize(file))
    val originUri = baseUri.resolve(originRelativeUri)
    val originPath = Paths.get(originUri.getPath).getParent
    def relativizeAttribute(element: Element, attribute: String): Unit = {
      val absoluteHref = URI.create(element.attr(s"abs:$attribute"))
      if (absoluteHref.getHost == baseUri.getHost) {
        val hrefPath = Paths.get(absoluteHref.getPath)
        val relativeHref = originPath.relativize(hrefPath)
        val fragment =
          if (absoluteHref.getFragment == null) ""
          else "#" + absoluteHref.getFragment
        val newHref = relativeUri(relativeHref).toString + fragment
        element.attr(attribute, newHref)
      } else if (element.attr(attribute).startsWith("//")) {
        // We force "//hostname" links to become "https://hostname" in order to make
        // the site browsable without file server. If we keep "//hostname"  unchanged
        // then users will try to load "file://hostname" which results in 404.
        // We hardcode https instead of http because it's OK to load https from http
        // but not the other way around.
        element.attr(attribute, "https:" + element.attr(attribute))
      }
    }
    val doc = Jsoup.parse(file.toFile, StandardCharsets.UTF_8.name(), originUri.toString)
    def relativizeElement(element: String, attribute: String): Unit =
      doc.select(element).forEach { element =>
        relativizeAttribute(element, attribute)
      }
    relativizeElement("a", "href")
    relativizeElement("link", "href")
    relativizeElement("img", "src")
    val renderedHtml = doc.outerHtml()
    Files.write(file, renderedHtml.getBytes(StandardCharsets.UTF_8))
  }

  private def relativeUri(relativePath: Path): URI = {
    require(!relativePath.isAbsolute, relativePath)
    val names = relativePath.iterator().asScala
    val uris = names.map { name =>
      new URI(null, null, name.toString, null)
    }
    URI.create(uris.mkString("", "/", ""))
  }
}

