package coursier.ivy

import coursier.core.Validation._
import coursier.core.{
  Classifier,
  Configuration,
  Dependency,
  DependencyManagement,
  Extension,
  Info,
  MinimizedExclusions,
  Module,
  ModuleName,
  Organization,
  Overrides,
  Project,
  Publication,
  Type,
  Variant,
  VariantSelector
}
import coursier.util.Xml._
import coursier.version.{Version, VersionConstraint}

import scala.collection.compat._

object IvyXml {

  val attributesNamespace = "http://ant.apache.org/ivy/extra"

  private def info(node: Node): Either[String, (Module, Version)] =
    for {
      org <- node
        .attribute("organisation")
        .flatMap(validateCoordinate(_, "organisation"))
        .map(Organization(_))
      name <- node
        .attribute("module")
        .flatMap(validateCoordinate(_, "module"))
        .map(ModuleName(_))
      version <- node
        .attribute("revision")
        .flatMap(validateCoordinate(_, "revision"))
    } yield {
      val attr = node.attributesFromNamespace(attributesNamespace)
      (Module(org, name, attr.toMap), Version(version))
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
          Configuration(name) -> node.attribute("extends").toSeq.flatMap(
            _.split(',').map(Configuration(_))
          )
      }

  // FIXME "default(runtime)" likely not to be always the default
  def mappings(mapping: String): Seq[(Configuration, Configuration)] =
    mapping.split(';').toSeq.flatMap { m =>
      val (froms, tos) = m.split("->", 2) match {
        case Array(from)     => (from, Configuration.defaultRuntime.value)
        case Array(from, to) => (from, to)
      }

      for {
        from <- froms.split(',').toSeq
        to   <- tos.split(',').toSeq
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
      .fold(Seq(Configuration.all))(_.split(',').toSeq.map(Configuration(_)))
    confs.map(_ -> (org, name))
  }

  private def override0(node0: Node): Option[(Organization, ModuleName, VersionConstraint)] = {
    val versionOpt = node0
      .attribute("rev")
      .toOption
      .filter(_.nonEmpty)
    versionOpt.map { version =>
      val org = Organization(node0.attribute("org").getOrElse("*"))
      val name = ModuleName(
        node0.attribute("module").toOption
          .orElse(node0.attribute("name").toOption)
          .getOrElse("*")
      )
      (org, name, VersionConstraint(version))
    }
  }

  // FIXME Errors ignored as above - warnings should be reported at least for anything suspicious
  private def dependencies(
    node: Node,
    globalExcludes: Map[Configuration, Set[(Organization, ModuleName)]],
    globalExcludesFilter: (Configuration, Organization, ModuleName) => Boolean,
    globalOverrides: Overrides
  ): Seq[(Variant, Dependency)] =
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
          .view
          .mapValues(_.map(_._2).toSet)
          .toMap

        val allConfsExcludes = excludes.getOrElse(Configuration.all, Set.empty)

        val attr = node.attributesFromNamespace(attributesNamespace)
        val transitive = node.attribute("transitive") match {
          case Right("false") => false
          case _              => true
        }

        for {
          org <- node
            .attribute("org")
            .flatMap(validateCoordinate(_, "org"))
            .toOption
            .toSeq
            .map(Organization(_))
          name <- node
            .attribute("name")
            .flatMap(validateCoordinate(_, "name"))
            .toOption
            .toSeq
            .map(ModuleName(_))
          version <- node
            .attribute("rev")
            .flatMap(validateCoordinate(_, "rev"))
            .toOption
            .toSeq
          rawConf            <- node.attribute("conf").toOption.toSeq
          (fromConf, toConf) <- mappings(rawConf)
          fromConf0 = Variant.Configuration(fromConf)
          if globalExcludesFilter(fromConf, org, name)
          pub <- publications
        } yield fromConf0 -> Dependency(
          Module(org, name, attr.toMap),
          VersionConstraint(version),
          VariantSelector.ConfigurationBased(toConf),
          globalExcludes.getOrElse(Configuration.all, Set.empty) ++
            globalExcludes.getOrElse(fromConf, Set.empty) ++
            allConfsExcludes ++
            excludes.getOrElse(fromConf, Set.empty),
          pub, // should come from possible artifact nodes
          optional = false,
          transitive = transitive
        ).withOverridesMap(globalOverrides)
      }

  private def publication(node: Node): Publication = {
    val name       = node.attribute("name").getOrElse("")
    val type0      = node.attribute("type").toOption.fold(Type.jar)(Type(_))
    val ext        = node.attribute("ext").toOption.fold(type0.asExtension)(Extension(_))
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

      val (module0, version) = modVer
      val (extraInfo, attr) = module0.attributes
        .partition(_._1.startsWith("info."))
      val module =
        if (extraInfo.isEmpty) module0
        else module0.withAttributes(attr)

      val dependenciesNodeOpt = node.children
        .find(_.label == "dependencies")

      val globalExcludes = dependenciesNodeOpt
        .map(_.children)
        .getOrElse(Nil)
        .filter(_.label == "exclude")
        .flatMap(exclude)
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2).toSet)
        .toMap
      val filter = {
        val filters       = globalExcludes.view.mapValues(set => MinimizedExclusions(set)).toMap
        val allConfFilter = filters.get(Configuration.all)
        (conf: Configuration, org: Organization, name: ModuleName) =>
          allConfFilter.forall(_(org, name)) && {
            val confFilter = filters.get(conf)
            confFilter.forall(_(org, name))
          }
      }

      // https://ant.apache.org/ivy/history/2.5.0-rc1/ivyfile/override.html
      val globalOverrides = Overrides {
        dependenciesNodeOpt
          .map(_.children)
          .getOrElse(Nil)
          .filter(_.label == "override")
          .flatMap(override0)
          .map {
            case (org, name, ver) =>
              DependencyManagement.Key(org, name, Type.jar, Classifier.empty) ->
                DependencyManagement.Values(
                  Configuration.empty,
                  ver,
                  MinimizedExclusions.zero,
                  optional = false
                )
          }
          .toMap
      }
      val dependencies0 = dependenciesNodeOpt
        .map(dependencies(_, globalExcludes, filter, globalOverrides))
        .getOrElse(Nil)

      val configurationsNodeOpt = node.children
        .find(_.label == "configurations")

      val configurationsOpt = configurationsNodeOpt.map(configurations)

      val configurations0 =
        configurationsOpt.getOrElse(Seq(Configuration.default -> Seq.empty[Configuration]))

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
        parent0 = None,
        dependencyManagement0 = Nil,
        properties = extraInfo.toSeq,
        profiles = Nil,
        versions = None,
        snapshotVersioning = None,
        packagingOpt = None,
        relocated = false,
        actualVersionOpt0 = None,
        if (publicationsOpt.isEmpty)
          // no publications node -> default JAR artifact
          Seq(Variant.Configuration(Configuration.all) -> Publication(
            module.name.value,
            Type.jar,
            Extension.jar,
            Classifier.empty
          ))
        else {
          // publications node is there -> only its content (if it is empty, no artifacts,
          // as per the Ivy manual)
          val inAllConfs = publicationsOpt.flatMap(_.get(Configuration.all)).getOrElse(Nil)
          configurations0.flatMap {
            case (conf, _) =>
              val conf0 = Variant.Configuration(conf)
              (publicationsOpt.flatMap(_.get(conf)).getOrElse(Nil) ++ inAllConfs).map(conf0 -> _)
          }
        },
        Info(
          description,
          homePage,
          licenses,
          Nil,
          publicationDate,
          None
        ),
        Overrides.empty,
        Map.empty,
        Map.empty
      )
    }

}
