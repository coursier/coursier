package coursier.maven

import coursier.core._
import coursier.core.Validation._
import coursier.util.SaxHandler
import coursier.version.{VersionConstraint, VersionParse}

import java.util.Objects
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

final class PomParser extends SaxHandler {
  import PomParser._

  private[this] val state = new State
  private[this] val b     = new java.lang.StringBuilder

  private val nodeStack = new java.util.ArrayDeque[HandlerMapNode]()
  nodeStack.push(HandlerMapNode.rootHandlerNode)

  def startElement(tagName: String): Unit = {
    val next = nodeStack.peek().next(tagName)
    nodeStack.push(next)

    b.setLength(0)

    next.handler match {
      case _: ContentHandler =>
      case s: SectionHandler =>
        s.start(state)
      case p: PropertyHandler =>
        p.name(state, tagName)
      case _ =>
    }
  }
  def characters(ch: Array[Char], start: Int, length: Int): Unit = {
    val parent = nodeStack.peek()
    if (parent ne null) {
      val readContent = parent.handler match {
        case _: PropertyHandler => true
        case _: ContentHandler  => true
        case _: SectionHandler  => false
        case _                  => false
      }
      if (readContent)
        b.append(ch, start, length)
    }
  }
  def endElement(tagName: String): Unit = {
    val node = nodeStack.pop()

    node.handler match {
      case p: PropertyHandler =>
        p.content(state, b.toString)
      case c: ContentHandler =>
        c.content(state, b.toString.trim)
      case s: SectionHandler =>
        s.end(state)
      case _ =>
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
        case h :: t    => CustomList.Cons(h, CustomList(t))
      }
  }

  private final class State {
    var groupId       = ""
    var artifactIdOpt = Option.empty[String]
    var version       = ""

    var parentGroupIdOpt    = Option.empty[String]
    var parentArtifactIdOpt = Option.empty[String]
    var parentVersion       = ""

    var description            = ""
    var url                    = ""
    val licenseInfo            = new ListBuffer[Info.License]
    var licenseName            = ""
    var licenseUrl             = Option.empty[String]
    var licenseDistribution    = Option.empty[String]
    var licenseComments        = Option.empty[String]
    val developers             = Nil                             // TODO
    val publication            = Option.empty[Versions.DateTime] // TODO
    var scmOpt                 = Option.empty[Info.Scm]
    var scmUrl                 = Option.empty[String]
    var scmConnection          = Option.empty[String]
    var scmDeveloperConnection = Option.empty[String]

    var packagingOpt = Option.empty[Type]

    val dependencies         = new ListBuffer[(Variant, Dependency)]
    val dependencyManagement = new ListBuffer[(Variant, Dependency)]

    val properties = new ListBuffer[(String, String)]

    var relocationGroupIdOpt    = Option.empty[Organization]
    var relocationArtifactIdOpt = Option.empty[ModuleName]
    var relocationVersionOpt    = Option.empty[VersionConstraint]

    var dependencyGroupIdOpt    = Option.empty[Organization]
    var dependencyArtifactIdOpt = Option.empty[ModuleName]
    var dependencyVersion       = ""
    var dependencyOptional      = false
    var dependencyScope         = Configuration.empty
    var dependencyType          = Type.empty
    var dependencyClassifier    = Classifier.empty
    var dependencyExclusions    = Set.empty[(Organization, ModuleName)]

    var dependencyExclusionGroupId    = Organization("*")
    var dependencyExclusionArtifactId = ModuleName("*")

    var propertyNameOpt = Option.empty[String]

    var profileId                     = ""
    val profileDependencies           = new ListBuffer[(Configuration, Dependency)]
    val profileDependencyManagement   = new ListBuffer[(Configuration, Dependency)]
    var profileProperties             = Map.empty[String, String]
    val profileActivationProperties   = new ListBuffer[(String, Option[String])]
    var profileActiveByDefaultOpt     = Option.empty[Boolean]
    var profilePropertyNameOpt        = Option.empty[String]
    var profilePropertyValueOpt       = Option.empty[String]
    var profileActivationOsArchOpt    = Option.empty[String]
    var profileActivationOsFamilyOpt  = Option.empty[String]
    var profileActivationOsNameOpt    = Option.empty[String]
    var profileActivationOsVersionOpt = Option.empty[String]
    var profileActivationJdkOpt =
      Option.empty[Either[coursier.version.VersionInterval, Seq[coursier.version.Version]]]

    val profiles = new ListBuffer[Profile]

    def project: Either[String, Project] = {

      val groupIdOpt = Some(groupId).filter(_.nonEmpty)
        .orElse(parentGroupIdOpt.filter(_.nonEmpty))

      val versionOpt = Some(version).filter(_.nonEmpty)
        .orElse(Some(parentVersion).filter(_.nonEmpty))

      val properties0 = properties.toList

      val parentModuleOpt =
        for {
          parentGroupId <- parentGroupIdOpt
            .toRight("Parent organization missing")
            .flatMap(validateCoordinate(_, "parent groupId"))
          parentArtifactId <- parentArtifactIdOpt
            .toRight("Parent artifactId missing")
            .flatMap(validateCoordinate(_, "parent artifactId"))
        } yield Module(Organization(parentGroupId), ModuleName(parentArtifactId), Map.empty)

      for {
        finalGroupId <- groupIdOpt
          .toRight("No organization found")
          .flatMap(validateCoordinate(_, "groupId"))
        artifactId <- artifactIdOpt
          .toRight("No artifactId found")
          .flatMap(validateCoordinate(_, "artifactId"))
        finalVersion <- versionOpt
          .toRight("No version found")
          .flatMap(validateCoordinate(_, "version"))
          .map(coursier.version.Version(_))

        _ <- {
          if (parentModuleOpt.exists(_.organization.value.isEmpty))
            Left("Parent organization missing")
          else
            Right(())
        }

        _ <- {
          if (parentModuleOpt.isRight && parentVersion.isEmpty)
            Left("No parent version found")
          else
            Right(())
        }

      } yield {

        val parentOpt = for {
          parentModule  <- parentModuleOpt
          parentVersion <- validateCoordinate(parentVersion, "parent version")
        } yield (parentModule, coursier.version.Version(parentVersion))

        val projModule = Module(Organization(finalGroupId), ModuleName(artifactId), Map.empty)

        val relocationDependencyOpt = {
          val isRelocated = relocationGroupIdOpt.nonEmpty ||
            relocationArtifactIdOpt.nonEmpty ||
            relocationVersionOpt.nonEmpty
          if (isRelocated)
            Some {
              Variant.emptyConfiguration -> Dependency(
                Module(
                  organization = relocationGroupIdOpt.getOrElse(projModule.organization),
                  name = relocationArtifactIdOpt.getOrElse(projModule.name),
                  attributes = projModule.attributes
                ),
                relocationVersionOpt.getOrElse(VersionConstraint.fromVersion(finalVersion)),
                VariantSelector.emptyConfiguration,
                Set.empty[(Organization, ModuleName)],
                Attributes.empty,
                optional = false,
                transitive = true
              )
            }
          else
            None
        }

        Project(
          projModule,
          finalVersion,
          relocationDependencyOpt.toList ::: dependencies.toList,
          Map.empty[Configuration, Seq[Configuration]],
          parentOpt.toOption,
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
            developers,
            publication,
            scmOpt,
            licenseInfo.toSeq
          ),
          Overrides.empty,
          Map.empty,
          Map.empty
        )
      }
    }
  }

  private sealed abstract class Handler(val path: List[String])
  private object NoHandler extends Handler(Nil)

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

  private val handlers: Seq[Handler] = Seq[Handler](
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
        state.relocationVersionOpt = Some(VersionConstraint(content))
    }
  ) ++ dependencyHandlers(
    "dependency" :: "dependencies" :: "project" :: Nil,
    (s, c, d) =>
      s.dependencies += Variant.Configuration(c) -> d
  ) ++ dependencyHandlers(
    "dependency" :: "dependencies" :: "dependencyManagement" :: "project" :: Nil,
    (s, c, d) =>
      s.dependencyManagement += Variant.Configuration(c) -> d
  ) ++ propertyHandlers(
    "properties" :: "project" :: Nil,
    (s, k, v) =>
      s.properties += k -> v
  ) ++ profileHandlers(
    "profile" :: "profiles" :: "project" :: Nil,
    (s, p) =>
      s.profiles += p
  ) ++ scmHandlers(
    "scm" :: "project" :: Nil,
    (s, scm) =>
      s.scmOpt = Some(scm)
  ) ++ licenseHandlers(
    "license" :: "licenses" :: "project" :: Nil,
    (s, l) =>
      s.licenseInfo += l
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
            case "true"  => Some(true)
            case "false" => Some(false)
            case _       => None
          }
      },
      new SectionHandler("property" :: "activation" :: prefix) {
        def start(state: State) = {
          state.profilePropertyNameOpt = None
          state.profilePropertyValueOpt = None
        }
        def end(state: State) =
          state.profileActivationProperties +=
            state.profilePropertyNameOpt.get -> state.profilePropertyValueOpt
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
            VersionParse.versionInterval(s)
              .orElse(VersionParse.multiVersionInterval(s))
              .map(Left(_))
              .orElse(VersionParse.version(s).map(v => Right(Seq(v))))
      }
    ) ++ dependencyHandlers(
      "dependency" :: "dependencies" :: prefix,
      (s, c, d) =>
        s.profileDependencies += c -> d
    ) ++ dependencyHandlers(
      "dependency" :: "dependencies" :: "dependencyManagement" :: prefix,
      (s, c, d) =>
        s.profileDependencyManagement += c -> d
    ) ++ propertyHandlers(
      "properties" :: prefix,
      (s, k, v) =>
        s.profileProperties = s.profileProperties + (k -> v)
    )

  private def dependencyHandlers(
    prefix: List[String],
    add: (State, Configuration, Dependency) => Unit
  ) =
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
            VersionConstraint(state.dependencyVersion),
            VariantSelector.emptyConfiguration,
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
        override def name(state: State, name: String) =
          state.propertyNameOpt = Some(name)
        override def content(state: State, content: String) = {
          add(state, state.propertyNameOpt.get, content)
          state.propertyNameOpt = None
        }
      }
    )

  private object HandlerMapNode {
    // This is called once at class-load time to build the immutable tree
    def build(paths: Seq[(Seq[String], Handler)]): HandlerMapNode = {
      val rootBuilder = new Builder
      for ((path, h) <- paths) rootBuilder.add(path.reverse.toList, h)
      rootBuilder.freeze()
    }

    // Temporary mutable class used ONLY during the build phase
    private class Builder {
      private val children                  = new mutable.HashMap[String, Builder]
      private var wildcard: Option[Builder] = None
      private var handler: Handler          = NoHandler

      def add(path: List[String], h: Handler): Unit = path match {
        case Nil =>
          if (this.handler ne NoHandler) throw new Exception("Handler already set")
          this.handler = h
        case "*" :: tail =>
          val w = wildcard.getOrElse(new Builder)
          wildcard = Some(w)
          w.add(tail, h)
        case head :: tail =>
          val child = children.getOrElseUpdate(head, new Builder)
          child.add(tail, h)
      }

      def freeze(): HandlerMapNode = {
        val frozenWildcard = wildcard.map(_.freeze()).getOrElse(EmptyHandlerMapNode)
        if (children.isEmpty && wildcard.isEmpty && handler == NoHandler)
          EmptyHandlerMapNode
        else if (children.size <= 8) {
          val k = children.keys.toArray
          val v = k.map(children(_).freeze())
          new SmallArrayNode(handler, k, v, frozenWildcard)
        }
        else
          new HashMapNode(
            handler,
            children.map { case (k, v) => k -> v.freeze() }.toMap,
            frozenWildcard
          )
      }
    }
    final val rootHandlerNode = build(handlers.map(h => (h.path, h)))

  }
  private sealed abstract class HandlerMapNode {
    def handler: Handler
    def next(tag: String): HandlerMapNode
  }

  private case object EmptyHandlerMapNode extends HandlerMapNode {
    val handler: Handler                  = NoHandler
    def next(tag: String): HandlerMapNode = this
  }

  /** Specialized node for 1-8 children: faster than HashMap due to cache locality */
  private final class SmallArrayNode(
    val handler: Handler,
    keys: Array[String],
    values: Array[HandlerMapNode],
    wildcard: HandlerMapNode
  ) extends HandlerMapNode {
    private val hashes = keys.map(_.hashCode)
    def next(tag: String): HandlerMapNode = {
      Objects.requireNonNull(tag)
      val h   = tag.hashCode
      val len = keys.length
      var i   = 0

      while (i < len) {
        // Identity check (hashes(i) == h) is significantly faster than string equals.
        // We only perform the full .equals check if the hash matches.
        if (hashes(i) == h && keys(i) == tag)
          return values(i)
        i += 1
      }

      // 3. Fallback to wildcard (e.g., the "*" or EmptyNode)
      wildcard
    }
  }

  /** Fallback for very large branches (unlikely in POM, but safe) */
  private final class HashMapNode(
    val handler: Handler,
    children: Map[String, HandlerMapNode],
    wildcard: HandlerMapNode
  ) extends HandlerMapNode {
    def next(tag: String): HandlerMapNode =
      children.getOrElse(tag, wildcard)
  }

  private def scmHandlers(prefix: List[String], add: (State, Info.Scm) => Unit) =
    Seq(
      new SectionHandler(prefix) {
        def start(state: State) = {}
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

  private def licenseHandlers(prefix: List[String], add: (State, Info.License) => Unit) =
    Seq(
      new SectionHandler(prefix) {
        def start(state: State): Unit = {}
        def end(state: State): Unit = {
          val license = Info.License(
            state.licenseName,
            state.licenseUrl,
            state.licenseDistribution,
            state.licenseComments
          )
          add(state, license)
        }
      },
      content("name" :: prefix) {
        (state, name) =>
          state.licenseName = name
      },
      content("url" :: prefix) {
        (state, url) =>
          state.licenseUrl = Some(url)
      },
      content("distribution" :: prefix) {
        (state, distribution) =>
          state.licenseDistribution = Some(distribution)
      },
      content("comments" :: prefix) {
        (state, comments) =>
          state.licenseComments = Some(comments)
      }
    )
}
