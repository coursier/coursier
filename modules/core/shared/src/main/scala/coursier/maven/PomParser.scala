package coursier.maven

import coursier.core._
import coursier.util.SaxHandler

import scala.collection.mutable.ListBuffer

final class PomParser extends SaxHandler {

  import PomParser._

  private[this] val state = new State

  private[this] var paths: CustomList[String] = CustomList.Nil
  private[this] var handlers = List.empty[Option[Handler]]

  private[this] val b = new java.lang.StringBuilder

  def startElement(tagName: String): Unit = {
    paths = tagName :: paths
    val handlerOpt = handlerMap.get(paths).orElse(handlerMap.get("*" :: paths.tail))
    handlers = handlerOpt :: handlers
    b.setLength(0)

    handlerOpt.foreach {
      case _: ContentHandler =>
      case s: SectionHandler =>
        s.start(state)
      case p: PropertyHandler =>
        p.name(state, tagName)
    }
  }
  def characters(ch: Array[Char], start: Int, length: Int): Unit = {
    val readContent = handlers.headOption.exists(_.exists {
      case _: PropertyHandler => true
      case _: ContentHandler => true
      case _: SectionHandler => false
    })
    if (readContent)
      b.append(ch, start, length)
  }
  def endElement(tagName: String): Unit = {
    val handlerOpt = handlers.headOption.flatten
    paths = paths.tail
    handlers = handlers.tail

    handlerOpt.foreach {
      case p: PropertyHandler =>
        p.content(state, b.toString)
      case c: ContentHandler =>
        c.content(state, b.toString.trim)
      case s: SectionHandler =>
        s.end(state)
    }

    b.setLength(0)
  }

  def project: Either[String, Project] =
    state.project
}

object PomParser {

  private sealed abstract class CustomList[+T] {
    def ::[U >: T](elem: U): CustomList[U] =
      CustomList.Cons(elem, this)
    def tail: CustomList[T]
  }

  private object CustomList {
    case object Nil extends CustomList[Nothing] {
      def tail: CustomList[Nothing] =
        throw new NoSuchElementException("tail of empty list")
    }
    final case class Cons[+T](head: T, override val tail: CustomList[T]) extends CustomList[T] {
      override val hashCode: Int =
        31 * (31 + head.hashCode) + tail.hashCode
    }
    def apply[T](l: List[T]): CustomList[T] =
      l match {
        case scala.Nil => CustomList.Nil
        case h :: t => CustomList.Cons(h, CustomList(t))
      }
  }

  private final class State {
    var groupId = ""
    var artifactIdOpt = Option.empty[String]
    var version = ""

    var parentGroupIdOpt = Option.empty[String]
    var parentArtifactIdOpt = Option.empty[String]
    var parentVersion = ""

    var description = ""
    var url = ""
    val licenses = Nil // TODO
    val developers = Nil // TODO
    val publication = Option.empty[Versions.DateTime] // TODO
    var scmOpt = Option.empty[Info.Scm]
    var scmUrl = Option.empty[String]
    var scmConnection = Option.empty[String]
    var scmDeveloperConnection = Option.empty[String]

    var packagingOpt = Option.empty[Type]

    val dependencies = new ListBuffer[(Configuration, Dependency)]
    val dependencyManagement = new ListBuffer[(Configuration, Dependency)]

    val properties = new ListBuffer[(String, String)]

    var relocationGroupIdOpt = Option.empty[Organization]
    var relocationArtifactIdOpt = Option.empty[ModuleName]
    var relocationVersionOpt = Option.empty[String]

    var dependencyGroupIdOpt = Option.empty[Organization]
    var dependencyArtifactIdOpt = Option.empty[ModuleName]
    var dependencyVersion = ""
    var dependencyOptional = false
    var dependencyScope = Configuration.empty
    var dependencyType = Type.empty
    var dependencyClassifier = Classifier.empty
    var dependencyExclusions = Set.empty[(Organization, ModuleName)]

    var dependencyExclusionGroupId = Organization("*")
    var dependencyExclusionArtifactId = ModuleName("*")

    var propertyNameOpt = Option.empty[String]

    var profileId = ""
    val profileDependencies = new ListBuffer[(Configuration, Dependency)]
    val profileDependencyManagement = new ListBuffer[(Configuration, Dependency)]
    var profileProperties = Map.empty[String, String]
    val profileActivationProperties = new ListBuffer[(String, Option[String])]
    var profileActiveByDefaultOpt = Option.empty[Boolean]
    var profilePropertyNameOpt = Option.empty[String]
    var profilePropertyValueOpt = Option.empty[String]
    var profileActivationOsArchOpt = Option.empty[String]
    var profileActivationOsFamilyOpt = Option.empty[String]
    var profileActivationOsNameOpt = Option.empty[String]
    var profileActivationOsVersionOpt = Option.empty[String]
    var profileActivationJdkOpt = Option.empty[Either[VersionInterval, Seq[Version]]]

    val profiles = new ListBuffer[Profile]

    def project: Either[String, Project] = {

      val groupIdOpt = Some(groupId).filter(_.nonEmpty)
        .orElse(parentGroupIdOpt.filter(_.nonEmpty))

      val versionOpt = Some(version).filter(_.nonEmpty)
        .orElse(Some(parentVersion).filter(_.nonEmpty))

      val properties0 = properties.toList

      val parentModuleOpt =
        (parentGroupIdOpt, parentArtifactIdOpt) match {
          case (Some(parentGroupId), Some(parentArtifactId)) =>
            Some(Module(Organization(parentGroupId), ModuleName(parentArtifactId), Map.empty))
          case _ =>
            None
        }

      for {
        finalGroupId <- groupIdOpt.toRight("No organization found")
        artifactId <- artifactIdOpt.toRight("No artifactId found")
        finalVersion <- versionOpt.toRight("No version found")

        _ <- {
          if (parentModuleOpt.exists(_.organization.value.isEmpty))
            Left("Parent organization missing")
          else
            Right(())
        }

        _ <- {
          if (parentModuleOpt.nonEmpty && parentVersion.isEmpty)
            Left("No parent version found")
          else
            Right(())
        }

        extraAttrs <- properties0
          .collectFirst { case ("extraDependencyAttributes", s) => Pom.extraAttributes(s) }
          .getOrElse(Right(Map.empty))

      } yield {

        val parentOpt = parentModuleOpt.map((_, parentVersion))

        val extraAttrsMap = extraAttrs
          .map {
            case (mod, ver) =>
              (mod.withAttributes(Map.empty), ver) -> mod.attributes
          }
          .toMap

        val projModule = Module(Organization(finalGroupId), ModuleName(artifactId), Map.empty)

        val relocationDependencyOpt =
          if (relocationGroupIdOpt.nonEmpty || relocationArtifactIdOpt.nonEmpty || relocationVersionOpt.nonEmpty)
            Some {
              Configuration.empty -> Dependency(
                projModule
                  .withOrganization(relocationGroupIdOpt.getOrElse(projModule.organization))
                  .withName(relocationArtifactIdOpt.getOrElse(projModule.name)),
                relocationVersionOpt.getOrElse(finalVersion),
                Configuration.empty,
                Set.empty[(Organization, ModuleName)],
                Attributes.empty,
                optional = false,
                transitive = true
              )
            }
          else
            None

        Project(
          projModule,
          finalVersion,
          (relocationDependencyOpt.toList ::: dependencies.toList).map {
            case (config, dep0) =>
              val dep = extraAttrsMap.get(dep0.moduleVersion).fold(dep0)(attrs =>
                dep0.withModule(dep0.module.withAttributes(attrs))
              )
              config -> dep
          },
          Map.empty,
          parentOpt,
          dependencyManagement.toList,
          properties0,
          profiles.toList,
          None,
          None,
          packagingOpt,
          relocated = relocationDependencyOpt.nonEmpty,
          None,
          Nil,
          Info(
            description,
            url,
            licenses,
            developers,
            publication,
            scmOpt
          )
        )
      }
    }
  }

  private sealed abstract class Handler(val path: List[String])

  private abstract class SectionHandler(path: List[String]) extends Handler(path) {
    def start(state: State): Unit
    def end(state: State): Unit
  }

  private abstract class ContentHandler(path: List[String]) extends Handler(path) {
    def content(state: State, content: String): Unit
  }

  private abstract class PropertyHandler(path: List[String]) extends Handler("*" :: path) {
    def name(state: State, name: String): Unit
    def content(state: State, content: String): Unit
  }

  private def content(path: List[String])(f: (State, String) => Unit): Handler =
    new ContentHandler(path) {
      def content(state: State, content: String) =
        f(state, content)
    }

  private val handlers = Seq[Handler](

    content("groupId" :: "project" :: Nil) {
      (state, content) =>
        state.groupId = content
    },
    content("artifactId" :: "project" :: Nil) {
      (state, content) =>
        state.artifactIdOpt = Some(content)
    },
    content("version" :: "project" :: Nil) {
      (state, content) =>
        state.version = content
    },

    content("groupId" :: "parent" :: "project" :: Nil) {
      (state, content) =>
        state.parentGroupIdOpt = Some(content)
    },
    content("artifactId" :: "parent" :: "project" :: Nil) {
      (state, content) =>
        state.parentArtifactIdOpt = Some(content)
    },
    content("version" :: "parent" :: "project" :: Nil) {
      (state, content) =>
        state.parentVersion = content
    },

    content("description" :: "project" :: Nil) {
      (state, content) =>
        state.description = content
    },
    content("url" :: "project" :: Nil) {
      (state, content) =>
        state.url = content
    },

    content("packaging" :: "project" :: Nil) {
      (state, content) =>
        state.packagingOpt = Some(Type(content))
    },

    content("groupId" :: "relocation" :: "distributionManagement" :: "project" :: Nil) {
      (state, content) =>
        state.relocationGroupIdOpt = Some(Organization(content))
    },
    content("artifactId" :: "relocation" :: "distributionManagement" :: "project" :: Nil) {
      (state, content) =>
        state.relocationArtifactIdOpt = Some(ModuleName(content))
    },
    content("version" :: "relocation" :: "distributionManagement" :: "project" :: Nil) {
      (state, content) =>
        state.relocationVersionOpt = Some(content)
    }

  ) ++ dependencyHandlers(
    "dependency" :: "dependencies" :: "project" :: Nil,
    (s, c, d) => {
      s.dependencies += c -> d
    }
  ) ++ dependencyHandlers(
    "dependency" :: "dependencies" :: "dependencyManagement" :: "project" :: Nil,
    (s, c, d) => {
      s.dependencyManagement += c -> d
    }
  ) ++ propertyHandlers(
    "properties" :: "project" :: Nil,
    (s, k, v) => {
      s.properties += k -> v
    }
  ) ++ profileHandlers(
    "profile" :: "profiles" :: "project" :: Nil,
    (s, p) => {
      s.profiles += p
    }
  ) ++ scmHandlers(
    "scm" :: "project" :: Nil,
    (s, scm) => {
      s.scmOpt = Some(scm)
    }
  )

  private def profileHandlers(prefix: List[String], add: (State, Profile) => Unit) =
    Seq(
      new SectionHandler(prefix) {
        def start(state: State) = {
          state.profileId = ""
          state.profileActiveByDefaultOpt = None
          state.profileDependencies.clear()
          state.profileDependencyManagement.clear()
          state.profileProperties = Map.empty
          state.profileActivationProperties.clear()
          state.profileActivationOsArchOpt = None
          state.profileActivationOsFamilyOpt = None
          state.profileActivationOsNameOpt = None
          state.profileActivationOsVersionOpt = None
          state.profileActivationJdkOpt = None
        }
        def end(state: State) = {
          val p = Profile(
            state.profileId,
            state.profileActiveByDefaultOpt,
            Activation(
              state.profileActivationProperties.toList,
              Activation.Os(
                state.profileActivationOsArchOpt,
                state.profileActivationOsFamilyOpt.toSet,
                state.profileActivationOsNameOpt,
                state.profileActivationOsVersionOpt
              ),
              state.profileActivationJdkOpt
            ),
            state.profileDependencies.toList,
            state.profileDependencyManagement.toList,
            state.profileProperties
          )
          add(state, p)
        }
      },
      content("id" :: prefix) {
        (state, content) =>
          state.profileId = content
      },
      content("activeByDefault" :: "activation" :: prefix) {
        (state, content) =>
          state.profileActiveByDefaultOpt = content match {
            case "true" => Some(true)
            case "false" => Some(false)
            case _ => None
          }
      },
      new SectionHandler("property" :: "activation" :: prefix) {
        def start(state: State) = {
          state.profilePropertyNameOpt = None
          state.profilePropertyValueOpt = None
        }
        def end(state: State) = {
          state.profileActivationProperties +=
            state.profilePropertyNameOpt.get -> state.profilePropertyValueOpt
        }
      },
      content("name" :: "property" :: "activation" :: prefix) {
        (state, content) =>
          state.profilePropertyNameOpt = Some(content)
      },
      content("value" :: "property" :: "activation" :: prefix) {
        (state, content) =>
          state.profilePropertyValueOpt = Some(content)
      },
      content("arch" :: "os" :: "activation" :: prefix) {
        (state, content) =>
          state.profileActivationOsArchOpt = Some(content)
      },
      content("family" :: "os" :: "activation" :: prefix) {
        (state, content) =>
          state.profileActivationOsFamilyOpt = Some(content)
      },
      content("name" :: "os" :: "activation" :: prefix) {
        (state, content) =>
          state.profileActivationOsNameOpt = Some(content)
      },
      content("version" :: "os" :: "activation" :: prefix) {
        (state, content) =>
          state.profileActivationOsVersionOpt = Some(content)
      },
      content("jdk" :: "activation" :: prefix) {
        (state, content) =>
          val s = content
          state.profileActivationJdkOpt =
            Parse.versionInterval(s)
              .orElse(Parse.multiVersionInterval(s))
              .map(Left(_))
              .orElse(Parse.version(s).map(v => Right(Seq(v))))
      }
    ) ++ dependencyHandlers(
      "dependency" :: "dependencies" :: prefix,
      (s, c, d) => {
        s.profileDependencies += c -> d
      }
    ) ++ dependencyHandlers(
      "dependency" :: "dependencies" :: "dependencyManagement" :: prefix,
      (s, c, d) => {
        s.profileDependencyManagement += c -> d
      }
    ) ++ propertyHandlers(
      "properties" :: prefix,
      (s, k, v) => {
        s.profileProperties = s.profileProperties + (k -> v)
      }
    )

  private def dependencyHandlers(prefix: List[String], add: (State, Configuration, Dependency) => Unit) =
    Seq(

      new SectionHandler(prefix) {
        def start(state: State) = {
          state.dependencyGroupIdOpt = None
          state.dependencyArtifactIdOpt = None
          state.dependencyVersion = ""
          state.dependencyOptional = false
          state.dependencyScope = Configuration.empty
          state.dependencyType = Type.empty
          state.dependencyClassifier = Classifier.empty
          state.dependencyExclusions = Set()
        }
        def end(state: State) = {
          val d = Dependency(
            Module(state.dependencyGroupIdOpt.get, state.dependencyArtifactIdOpt.get, Map.empty),
            state.dependencyVersion,
            Configuration.empty,
            state.dependencyExclusions,
            Attributes(state.dependencyType, state.dependencyClassifier),
            state.dependencyOptional,
            transitive = true
          )
          add(state, state.dependencyScope, d)
        }
      },
      content("groupId" :: prefix) {
        (state, content) =>
          state.dependencyGroupIdOpt = Some(Organization(content))
      },
      content("artifactId" :: prefix) {
        (state, content) =>
          state.dependencyArtifactIdOpt = Some(ModuleName(content))
      },
      content("version" :: prefix) {
        (state, content) =>
          state.dependencyVersion = content
      },
      content("optional" :: prefix) {
        (state, content) =>
          state.dependencyOptional = content == "true"
      },
      content("scope" :: prefix) {
        (state, content) =>
          state.dependencyScope = Configuration(content)
      },
      content("type" :: prefix) {
        (state, content) =>
          state.dependencyType = Type(content)
      },
      content("classifier" :: prefix) {
        (state, content) =>
          state.dependencyClassifier = Classifier(content)
      },

      new SectionHandler("exclusion" :: "exclusions" :: prefix) {
        def start(state: State) = {
          state.dependencyExclusionGroupId = Organization("*")
          state.dependencyExclusionArtifactId = ModuleName("*")
        }
        def end(state: State) = {
          val r = (state.dependencyExclusionGroupId, state.dependencyExclusionArtifactId)
          state.dependencyExclusions = state.dependencyExclusions + r
        }
      },
      content("groupId" :: "exclusion" :: "exclusions" :: prefix) {
        (state, content) =>
          state.dependencyExclusionGroupId = Organization(content)
      },
      content("artifactId" :: "exclusion" :: "exclusions" :: prefix) {
        (state, content) =>
          state.dependencyExclusionArtifactId = ModuleName(content)
      }
    )

  private def propertyHandlers(prefix: List[String], add: (State, String, String) => Unit) =
    Seq(
      new PropertyHandler(prefix) {
        override def name(state: State, name: String) = {
          state.propertyNameOpt = Some(name)
        }
        override def content(state: State, content: String) = {
          add(state, state.propertyNameOpt.get, content)
          state.propertyNameOpt = None
        }
      }
    )

  private val handlerMap = handlers
    .map { h =>
      CustomList(h.path) -> h
    }
    .toMap

  private def scmHandlers(prefix: List[String], add: (State, Info.Scm) => Unit) =
    Seq(
      new SectionHandler(prefix) {
        def start(state: State) = {
        }
        def end(state: State) = {
          val d = Info.Scm(
            url = state.scmUrl,
            connection = state.scmConnection,
            developerConnection = state.scmDeveloperConnection
          )
          add(state, d)
        }
      },
      content("url" :: prefix) {
        (state, content) =>
          state.scmUrl = Some(content)
      },
      content("connection" :: prefix) {
        (state, content) =>
          state.scmConnection = Some(content)
      },
      content("developerConnection" :: prefix) {
        (state, content) =>
          state.scmDeveloperConnection = Some(content)
      }
    )
}
