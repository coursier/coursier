package coursier.ivy

import coursier.core._
import coursier.util.Xml._

object IvyXml {

  val attributesNamespace = "http://ant.apache.org/ivy/extra"

  private def info(node: Node): Either[String, (Module, String)] =
    for {
      org <- node
        .attribute("organisation")
        .map(Organization(_))
      name <- node
        .attribute("module")
        .map(ModuleName(_))
      version <- node.attribute("revision")
    } yield {
      val attr = node.attributesFromNamespace(attributesNamespace)
      (Module(org, name, attr.toMap), version)
    }

  // FIXME Errors are ignored here
  private def configurations(node: Node): Seq[(Configuration, Seq[Configuration])] =
    node
      .children
      .filter(_.label == "conf")
      .flatMap { node =>
        node.attribute("name").toOption.toSeq.map(_ -> node)
      }
      .map {
        case (name, node) =>
          Configuration(name) -> node.attribute("extends").toSeq.flatMap(_.split(',').map(Configuration(_)))
      }

  // FIXME "default(compile)" likely not to be always the default
  def mappings(mapping: String): Seq[(Configuration, Configuration)] =
    mapping.split(';').toSeq.flatMap { m =>
      val (froms, tos) = m.split("->", 2) match {
        case Array(from) => (from, Configuration.defaultCompile.value)
        case Array(from, to) => (from, to)
      }

      for {
        from <- froms.split(',').toSeq
        to <- tos.split(',').toSeq
      } yield (Configuration(from.trim), Configuration(to.trim))
    }

  private def exclude(node0: Node): Seq[(Configuration, (Organization, ModuleName))] = {
    val org = Organization(node0.attribute("org").getOrElse("*"))
    val name = ModuleName(
      node0.attribute("module").toOption
        .orElse(node0.attribute("name").toOption)
        .getOrElse("*")
    )
    val confs = node0
      .attribute("conf")
      .toOption
      .filter(_.nonEmpty)
      .fold(Seq(Configuration.all))(_.split(',').map(Configuration(_)))
    confs.map(_ -> (org, name))
  }

  // FIXME Errors ignored as above - warnings should be reported at least for anything suspicious
  private def dependencies(
    node: Node,
    globalExcludes: Map[Configuration, Set[(Organization, ModuleName)]],
    globalExcludesFilter: (Configuration, Organization, ModuleName) => Boolean
  ): Seq[(Configuration, Dependency)] =
    node.children
      .filter(_.label == "dependency")
      .flatMap { node =>
        // "include" sub-nodes are ignored here

        val artifacts = node
          .children
          .filter(_.label == "artifact")
          .map(publication)

        val publications =
          if (artifacts.isEmpty)
            Seq(Publication.empty)
          else
            artifacts

        val excludes = node
          .children
          .filter(_.label == "exclude")
          .flatMap(exclude)
          .groupBy(_._1)
          .mapValues(_.map(_._2).toSet)
          .toMap

        val allConfsExcludes = excludes.getOrElse(Configuration.all, Set.empty)

        val attr = node.attributesFromNamespace(attributesNamespace)
        val transitive = node.attribute("transitive") match {
          case Right("false") => false
          case _ => true
        }

        for {
          org <- node
            .attribute("org")
            .toOption
            .toSeq
            .map(Organization(_))
          name <- node
            .attribute("name")
            .toOption
            .toSeq
            .map(ModuleName(_))
          version <- node.attribute("rev").toOption.toSeq
          rawConf <- node.attribute("conf").toOption.toSeq
          (fromConf, toConf) <- mappings(rawConf)
          if globalExcludesFilter(fromConf, org, name)
          pub <- publications
        } yield {
          fromConf -> Dependency(
            Module(org, name, attr.toMap),
            version,
            toConf,
            globalExcludes.getOrElse(Configuration.all, Set.empty) ++
            globalExcludes.getOrElse(fromConf, Set.empty) ++
              allConfsExcludes ++
              excludes.getOrElse(fromConf, Set.empty),
            pub, // should come from possible artifact nodes
            optional = false,
            transitive = transitive
          )
        }
      }

  private def publication(node: Node): Publication = {
    val name = node.attribute("name").getOrElse("")
    val type0 = node.attribute("type").toOption.fold(Type.jar)(Type(_))
    val ext = node.attribute("ext").toOption.fold(type0.asExtension)(Extension(_))
    val classifier = node.attribute("classifier").toOption.fold(Classifier.empty)(Classifier(_))
    Publication(name, type0, ext, classifier)
  }

  private def publications(node: Node): Map[Configuration, Seq[Publication]] =
    node.children
      .filter(_.label == "artifact")
      .flatMap { node =>
        val pub = publication(node)
        val confs = node
          .attribute("conf")
          .fold(_ => Seq(Configuration.all), _.split(',').toSeq.map(Configuration(_)))
        confs.map(_ -> pub)
      }
      .groupBy { case (conf, _) => conf }
      .map { case (conf, l) => conf -> l.map { case (_, p) => p } }

  def project(node: Node): Either[String, Project] =
    for {
      infoNode <- node.children
        .find(_.label == "info")
        .toRight("Info not found")

      modVer <- info(infoNode)
    } yield {

      val (module, version) = modVer

      val dependenciesNodeOpt = node.children
        .find(_.label == "dependencies")

      val globalExcludes = dependenciesNodeOpt
        .map(_.children)
        .getOrElse(Nil)
        .filter(_.label == "exclude")
        .flatMap(exclude)
        .groupBy(_._1)
        .mapValues(_.map(_._2).toSet)
        .toMap
      val filter = {
        val filters = globalExcludes.mapValues(set => Exclusions(set)).toMap
        val allConfFilter = filters.get(Configuration.all)
        (conf: Configuration, org: Organization, name: ModuleName) =>
          allConfFilter.forall(_(org, name)) && {
            val confFilter = filters.get(conf)
            confFilter.forall(_(org, name))
          }
      }
      val dependencies0 = dependenciesNodeOpt.map(dependencies(_, globalExcludes, filter)).getOrElse(Nil)

      val configurationsNodeOpt = node.children
        .find(_.label == "configurations")

      val configurationsOpt = configurationsNodeOpt.map(configurations)

      val configurations0 = configurationsOpt.getOrElse(Seq(Configuration.default -> Seq.empty[Configuration]))

      val publicationsNodeOpt = node.children
        .find(_.label == "publications")

      val publicationsOpt = publicationsNodeOpt.map(publications)

      val descriptionNodeOpt = infoNode.children.find(_.label == "description")

      val description = descriptionNodeOpt
        .map(_.textContent.trim)
        .getOrElse("")

      val homePage = descriptionNodeOpt.flatMap(_.attribute("homepage").toOption).getOrElse("")

      val licenses = infoNode.children
        .filter(_.label == "license")
        .flatMap { n =>
          n.attribute("name").toSeq.map { name =>
            (name, n.attribute("url").toOption)
          }
        }

      val publicationDate = infoNode.attribute("publication").toOption.flatMap(parseDateTime)

      Project(
        module,
        version,
        dependencies0,
        configurations0.toMap,
        None,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        relocated = false,
        None,
        if (publicationsOpt.isEmpty)
          // no publications node -> default JAR artifact
          Seq(Configuration.all -> Publication(module.name.value, Type.jar, Extension.jar, Classifier.empty))
        else {
          // publications node is there -> only its content (if it is empty, no artifacts,
          // as per the Ivy manual)
          val inAllConfs = publicationsOpt.flatMap(_.get(Configuration.all)).getOrElse(Nil)
          configurations0.flatMap { case (conf, _) =>
            (publicationsOpt.flatMap(_.get(conf)).getOrElse(Nil) ++ inAllConfs).map(conf -> _)
          }
        },
        Info(
          description,
          homePage,
          licenses,
          Nil,
          publicationDate,
          None
        )
      )
    }

}
