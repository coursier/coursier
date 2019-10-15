package coursier.ivy

import coursier.core._
import coursier.util.Xml._

object IvyXml {

  val attributesNamespace = "http://ant.apache.org/ivy/extra"

  private def info(node: Node): Either[String, (Module, String)] =
    for {
      org <- node
        .attribute("organisation")
        .right
        .map(Organization(_))
        .right
      name <- node
        .attribute("module")
        .right
        .map(ModuleName(_))
        .right
      version <- node.attribute("revision").right
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
        node.attribute("name").right.toOption.toSeq.map(_ -> node)
      }
      .map {
        case (name, node) =>
          Configuration(name) -> node.attribute("extends").right.toSeq.flatMap(_.split(',').map(Configuration(_)))
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

  // FIXME Errors ignored as above - warnings should be reported at least for anything suspicious
  private def dependencies(node: Node): Seq[(Configuration, Dependency)] =
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
          .flatMap { node0 =>
            val org = Organization(node0.attribute("org").right.getOrElse("*"))
            val name = ModuleName(
              node0.attribute("module").right.toOption
                .orElse(node0.attribute("name").right.toOption)
                .getOrElse("*")
            )
            val confs = node0
              .attribute("conf")
              .right.toOption
              .filter(_.nonEmpty)
              .fold(Seq(Configuration.all))(_.split(',').map(Configuration(_)))
            confs.map(_ -> (org, name))
          }
          .groupBy { case (conf, _) => conf }
          .map { case (conf, l) => conf -> l.map { case (_, e) => e }.toSet }

        val allConfsExcludes = excludes.getOrElse(Configuration.all, Set.empty)

        val attr = node.attributesFromNamespace(attributesNamespace)
        val transitive = node.attribute("transitive") match {
          case Right("false") => false
          case _ => true
        }

        for {
          org <- node
            .attribute("org")
            .right
            .toOption
            .toSeq
            .map(Organization(_))
          name <- node
            .attribute("name")
            .right
            .toOption
            .toSeq
            .map(ModuleName(_))
          version <- node.attribute("rev").right.toOption.toSeq
          rawConf <- node.attribute("conf").right.toOption.toSeq
          (fromConf, toConf) <- mappings(rawConf)
          pub <- publications
        } yield {
          fromConf -> Dependency(
            Module(org, name, attr.toMap),
            version,
            toConf,
            allConfsExcludes ++ excludes.getOrElse(fromConf, Set.empty),
            pub, // should come from possible artifact nodes
            optional = false,
            transitive = transitive
          )
        }
      }

  private def publication(node: Node): Publication = {
    val name = node.attribute("name").right.getOrElse("")
    val type0 = node.attribute("type")
      .right.map(Type(_))
      .right.getOrElse(Type.jar)
    val ext = node.attribute("ext")
      .right.map(Extension(_))
      .right.getOrElse(type0.asExtension)
    val classifier = node.attribute("classifier")
      .right.map(Classifier(_))
      .right.getOrElse(Classifier.empty)
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
        .right

      modVer <- info(infoNode).right
    } yield {

      val (module, version) = modVer

      val dependenciesNodeOpt = node.children
        .find(_.label == "dependencies")

      val dependencies0 = dependenciesNodeOpt.map(dependencies).getOrElse(Nil)

      val configurationsNodeOpt = node.children
        .find(_.label == "configurations")

      val configurationsOpt = configurationsNodeOpt.map(configurations)

      val configurations0 = configurationsOpt.getOrElse(Seq(Configuration.default -> Seq.empty[Configuration]))

      val publicationsNodeOpt = node.children
        .find(_.label == "publications")

      val publicationsOpt = publicationsNodeOpt.map(publications)

      val description = infoNode.children
        .find(_.label == "description")
        .map(_.textContent.trim)
        .getOrElse("")

      val licenses = infoNode.children
        .filter(_.label == "license")
        .flatMap { n =>
          n.attribute("name").right.toSeq.map { name =>
            (name, n.attribute("url").right.toOption)
          }
        }

      val publicationDate = infoNode.attribute("publication")
        .right
        .toOption
        .flatMap(parseDateTime)

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
          "",
          licenses,
          Nil,
          publicationDate,
          None
        )
      )
    }

}
