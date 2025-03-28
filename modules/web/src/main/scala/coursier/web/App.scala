package coursier.web

import coursier.core.{
  Configuration,
  Dependency,
  Module,
  ModuleName,
  Organization,
  Resolution,
  Type,
  VariantSelector
}
import coursier.maven.{MavenRepository, MavenRepositoryLike}
import coursier.util.StringInterpolators._
import coursier.version.{Version, VersionConstraint}
import japgolly.scalajs.react.vdom.{Attr, TagMod}
import japgolly.scalajs.react.vdom.HtmlAttrs.dangerouslySetInnerHtml
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import scala.scalajs.js.Dynamic.{global => g}

object App {

  lazy val arbor = g.arbor

  val resultDependencies = ScalaComponent.builder[(Resolution, Backend)]("Result")
    .render_P {
      case (res, backend) =>
        def infoLabel(label: String) =
          <.span(^.`class` := "label label-info", label)
        def errorPopOver(label: String, desc: String) =
          popOver("danger", label, desc)
        def infoPopOver(label: String, desc: String) =
          popOver("info", label, desc)
        def popOver(`type`: String, label: String, desc: String) =
          <.button(
            ^.`type`               := "button",
            ^.`class`              := s"btn btn-xs btn-${`type`}",
            Attr("data-trigger")   := "focus",
            Attr("data-toggle")    := "popover",
            Attr("data-placement") := "bottom",
            Attr("data-content")   := desc,
            ^.onClick ==> backend.enablePopover,
            ^.onMouseOver ==> backend.enablePopover,
            label
          )

        def depItem(dep: Dependency, finalVersionOpt: Option[Version]) =
          <.tr(
            ^.`class` := (
              if (res.errorCache.contains(dep.moduleVersionConstraint)) "danger"
              else ""
            ),
            <.td(dep.module.organization.value),
            <.td(dep.module.name.value),
            <.td(finalVersionOpt.fold(dep.versionConstraint.asString)(finalVersion =>
              s"${finalVersion.asString} (for ${dep.versionConstraint.asString})"
            )),
            <.td(TagMod(
              if (dep.variantSelector == VariantSelector.ConfigurationBased(Configuration.compile))
                TagMod()
              else TagMod(infoLabel(dep.variantSelector.repr)),
              if (dep.attributes.`type`.isEmpty || dep.attributes.`type` == Type.jar) TagMod()
              else TagMod(infoLabel(dep.attributes.`type`.value)),
              if (dep.attributes.classifier.isEmpty) TagMod()
              else TagMod(infoLabel(dep.attributes.classifier.value)),
              Some(dep.minimizedExclusions.toSeq())
                .filter(_.nonEmpty)
                .map { excls =>
                  infoPopOver(
                    "Exclusions",
                    excls.sorted.map { case (org, name) => s"$org:$name" }.mkString("; ")
                  )
                }
                .toSeq
                .toTagMod,
              if (dep.optional) TagMod(infoLabel("optional")) else TagMod(),
              res
                .errorCache
                .get(dep.moduleVersionConstraint)
                .map(errs => errorPopOver("Error", errs.mkString("; ")))
                .toSeq
                .toTagMod
            )),
            <.td(TagMod(
              res.projectCache0.get(dep.moduleVersionConstraint) match {
                case Some((source: MavenRepositoryLike, proj)) =>
                  // FIXME Maven specific, generalize with source.artifacts
                  val version0 =
                    finalVersionOpt.map(_.asString) getOrElse dep.versionConstraint.asString
                  val relPath =
                    dep.module.organization.value.split('.').toSeq ++ Seq(
                      dep.module.name.value,
                      version0,
                      s"${dep.module.name.value}-$version0"
                    )

                  val root = source.root + "/"

                  TagMod(
                    <.a(
                      ^.href := s"$root${relPath.mkString("/")}.pom",
                      <.span(^.`class` := "label label-info", "POM")
                    ),
                    <.a(
                      ^.href := s"$root${relPath.mkString("/")}.jar",
                      <.span(^.`class` := "label label-info", "JAR")
                    )
                  )

                case _ => TagMod()
              }
            ))
          )

        val sortedDeps = res.minDependencies.toList
          .sortBy { dep =>
            (dep.module.organization, dep.module.name)
          }

        <.table(
          ^.`class` := "table",
          <.thead(
            <.tr(
              <.th("Organization"),
              <.th("Name"),
              <.th("Version"),
              <.th("Extra"),
              <.th("Links")
            )
          ),
          <.tbody(
            sortedDeps
              .map(dep =>
                depItem(
                  dep,
                  res
                    .projectCache0
                    .get(dep.moduleVersionConstraint)
                    .map(_._2.version0)
                    .filter(_ != dep.versionConstraint)
                )
              )
              .toTagMod
          )
        )
    }
    .build

  object icon {
    def apply(id: String) = <.span(^.`class` := s"glyphicon glyphicon-$id", ^.aria.hidden := true)
    def ok                = apply("ok")
    def edit              = apply("pencil")
    def remove            = apply("remove")
    def up                = apply("arrow-up")
    def down              = apply("arrow-down")
  }

  val moduleEditModal =
    ScalaComponent.builder[((Module, VersionConstraint), Int, Backend)]("EditModule")
      .render_P {
        case ((module, version), moduleIdx, backend) =>
          <.div(
            ^.`class`         := "modal fade",
            ^.id              := "moduleEdit",
            ^.role            := "dialog",
            ^.aria.labelledBy := "moduleEditTitle",
            <.div(
              ^.`class` := "modal-dialog",
              <.div(
                ^.`class` := "modal-content",
                <.div(
                  ^.`class` := "modal-header",
                  <.button(
                    ^.`type`             := "button",
                    ^.`class`            := "close",
                    Attr("data-dismiss") := "modal",
                    ^.aria.label         := "Close",
                    <.span(^.aria.hidden := true, dangerouslySetInnerHtml := "&times;")
                  ),
                  <.h4(^.`class` := "modal-title", ^.id := "moduleEditTitle", "Dependency")
                ),
                <.div(
                  ^.`class` := "modal-body",
                  <.form(
                    <.div(
                      ^.`class` := "form-group",
                      <.label(^.`for` := "inputOrganization", "Organization"),
                      <.input(
                        ^.`class`     := "form-control",
                        ^.id          := "inputOrganization",
                        ^.placeholder := "Organization",
                        ^.onChange ==> backend.updateModule(
                          moduleIdx,
                          (dep, value) =>
                            dep.withModule(dep.module.withOrganization(Organization(value)))
                        ),
                        ^.value := module.organization.value
                      )
                    ),
                    <.div(
                      ^.`class` := "form-group",
                      <.label(^.`for` := "inputName", "Name"),
                      <.input(
                        ^.`class`     := "form-control",
                        ^.id          := "inputName",
                        ^.placeholder := "Name",
                        ^.onChange ==> backend.updateModule(
                          moduleIdx,
                          (dep, value) => dep.withModule(dep.module.withName(ModuleName(value)))
                        ),
                        ^.value := module.name.value
                      )
                    ),
                    <.div(
                      ^.`class` := "form-group",
                      <.label(^.`for` := "inputVersion", "Version"),
                      <.input(
                        ^.`class`     := "form-control",
                        ^.id          := "inputVersion",
                        ^.placeholder := "Version",
                        ^.onChange ==> backend.updateModule(
                          moduleIdx,
                          (dep, value) => dep.withVersionConstraint(VersionConstraint(value))
                        ),
                        ^.value := version.asString
                      )
                    ),
                    <.div(
                      ^.`class` := "modal-footer",
                      <.button(
                        ^.`type`             := "submit",
                        ^.`class`            := "btn btn-primary",
                        Attr("data-dismiss") := "modal",
                        "Done"
                      )
                    )
                  )
                )
              )
            )
          )
      }
      .build

  val modules = ScalaComponent.builder[(Seq[Dependency], Int, Backend)]("Dependencies")
    .render_P {
      case (deps, editModuleIdx, backend) =>
        def depItem(dep: Dependency, idx: Int) =
          <.tr(
            <.td(dep.module.organization.value),
            <.td(dep.module.name.value),
            <.td(dep.versionConstraint.asString),
            <.td(
              <.a(
                Attr("data-toggle") := "modal",
                Attr("data-target") := "#moduleEdit",
                ^.`class`           := "icon-action",
                ^.onClick ==> backend.editModule(idx),
                icon.edit
              )
            ),
            <.td(
              <.a(
                Attr("data-toggle") := "modal",
                Attr("data-target") := "#moduleRemove",
                ^.`class`           := "icon-action",
                ^.onClick ==> backend.removeModule(idx),
                icon.remove
              )
            )
          )

        <.div(
          <.p(
            <.button(
              ^.`type`  := "button",
              ^.`class` := "btn btn-default customButton",
              ^.onClick ==> backend.addModule,
              Attr("data-toggle") := "modal",
              Attr("data-target") := "#moduleEdit",
              "Add"
            )
          ),
          <.table(
            ^.`class` := "table",
            <.thead(
              <.tr(
                <.th("Organization"),
                <.th("Name"),
                <.th("Version"),
                <.th(""),
                <.th("")
              )
            ),
            <.tbody(
              deps
                .zipWithIndex
                .map((depItem _).tupled)
                .toTagMod
            )
          ),
          moduleEditModal((
            deps
              .lift(editModuleIdx)
              .fold(
                Module(org"", name"", Map.empty) -> VersionConstraint.empty
              )(_.moduleVersionConstraint),
            editModuleIdx,
            backend
          ))
        )
    }
    .build

  val repoEditModal = ScalaComponent.builder[((String, MavenRepository), Int, Backend)]("EditRepo")
    .render_P {
      case ((name, repo), repoIdx, backend) =>
        <.div(
          ^.`class`         := "modal fade",
          ^.id              := "repoEdit",
          ^.role            := "dialog",
          ^.aria.labelledBy := "repoEditTitle",
          <.div(
            ^.`class` := "modal-dialog",
            <.div(
              ^.`class` := "modal-content",
              <.div(
                ^.`class` := "modal-header",
                <.button(
                  ^.`type`             := "button",
                  ^.`class`            := "close",
                  Attr("data-dismiss") := "modal",
                  ^.aria.label         := "Close",
                  <.span(^.aria.hidden := true, dangerouslySetInnerHtml := "&times;")
                ),
                <.h4(^.`class` := "modal-title", ^.id := "repoEditTitle", "Repository")
              ),
              <.div(
                ^.`class` := "modal-body",
                <.form(
                  <.div(
                    ^.`class` := "form-group",
                    <.label(^.`for` := "inputName", "Name"),
                    <.input(
                      ^.`class`     := "form-control",
                      ^.id          := "inputName",
                      ^.placeholder := "Name",
                      ^.onChange ==> backend.updateRepo(repoIdx, (item, value) => (value, item._2)),
                      ^.value := name
                    )
                  ),
                  <.div(
                    ^.`class` := "form-group",
                    <.label(^.`for` := "inputVersion", "Root"),
                    <.input(
                      ^.`class`     := "form-control",
                      ^.id          := "inputVersion",
                      ^.placeholder := "Root",
                      ^.onChange ==> backend.updateRepo(
                        repoIdx,
                        (item, value) => (item._1, item._2.withRoot(value))
                      ),
                      ^.value := repo.root
                    )
                  ),
                  <.div(
                    ^.`class` := "modal-footer",
                    <.button(
                      ^.`type`             := "submit",
                      ^.`class`            := "btn btn-primary",
                      Attr("data-dismiss") := "modal",
                      "Done"
                    )
                  )
                )
              )
            )
          )
        )
    }
    .build

  val repositories =
    ScalaComponent.builder[(Seq[(String, MavenRepository)], Int, Backend)]("Repositories")
      .render_P {
        case (repos, editRepoIdx, backend) =>
          def repoItem(item: (String, MavenRepository), idx: Int, isLast: Boolean) =
            <.tr(
              <.td(item._1),
              <.td(item._2.root),
              <.td(
                <.a(
                  Attr("data-toggle") := "modal",
                  Attr("data-target") := "#repoEdit",
                  ^.`class`           := "icon-action",
                  ^.onClick ==> backend.editRepo(idx),
                  icon.edit
                )
              ),
              <.td(
                <.a(
                  Attr("data-toggle") := "modal",
                  Attr("data-target") := "#repoRemove",
                  ^.`class`           := "icon-action",
                  ^.onClick ==> backend.removeRepo(idx),
                  icon.remove
                )
              ),
              <.td(
                if (idx > 0)
                  <.a(
                    Attr("data-toggle") := "modal",
                    Attr("data-target") := "#repoUp",
                    ^.`class`           := "icon-action",
                    ^.onClick ==> backend.moveRepo(idx, up = true),
                    icon.up
                  )
                else
                  TagMod()
              ),
              <.td(
                if (isLast)
                  TagMod()
                else
                  <.a(
                    Attr("data-toggle") := "modal",
                    Attr("data-target") := "#repoDown",
                    ^.`class`           := "icon-action",
                    ^.onClick ==> backend.moveRepo(idx, up = false),
                    icon.down
                  )
              )
            )

          <.div(
            <.p(
              <.button(
                ^.`type`  := "button",
                ^.`class` := "btn btn-default customButton",
                ^.onClick ==> backend.addRepo,
                Attr("data-toggle") := "modal",
                Attr("data-target") := "#repoEdit",
                "Add"
              )
            ),
            <.table(
              ^.`class` := "table",
              <.thead(
                <.tr(
                  <.th("Name"),
                  <.th("Root"),
                  <.th(""),
                  <.th(""),
                  <.th(""),
                  <.th("")
                )
              ),
              <.tbody {
                val elems =
                  repos
                    .init
                    .zipWithIndex
                    .map(t => repoItem(t._1, t._2, isLast = false)) ++
                    repos.lastOption.map(repoItem(_, repos.length - 1, isLast = true))
                elems.toTagMod
              }
            ),
            repoEditModal((
              repos
                .lift(editRepoIdx)
                .getOrElse("" -> MavenRepository("")),
              editRepoIdx,
              backend
            ))
          )
      }
      .build

  val options = ScalaComponent.builder[(ResolutionOptions, Backend)]("ResolutionOptions")
    .render_P {
      case (options, backend) =>
        <.div(
          <.div(
            ^.`class` := "checkbox",
            <.label(
              <.input.checkbox(
                ^.onChange ==> backend.options.toggleOptional,
                if (options.followOptional) ^.checked := true else TagMod()
              ),
              "Follow optional dependencies"
            )
          )
        )
    }
    .build

  val resolution = ScalaComponent.builder[(Option[Resolution], Backend)]("Resolution")
    .render_P {
      case (resOpt, backend) =>
        resOpt match {
          case Some(res) =>
            <.div(
              <.div(^.`class` := "page-header", <.h1("Resolution")),
              resultDependencies((res, backend))
            )

          case None =>
            <.div()
        }
    }
    .build

  val initialState = State(
    List(
      dep"io.get-coursier:coursier_2.13:2.0.0-RC6-15" // DEBUG
    ),
    Seq("central" -> MavenRepository("https://repo1.maven.org/maven2")),
    ResolutionOptions(),
    None,
    -1,
    -1,
    resolving = false,
    reverseTree = false,
    log = Nil
  )

  val app = ScalaComponent.builder[Unit]("Coursier")
    .initialState(initialState)
    .backend(new Backend(_))
    .render { scope =>

      val S       = scope.state
      val backend = scope.backend

      <.div(
        <.div(
          ^.role := "tabpanel",
          <.ul(
            ^.`class` := "nav nav-tabs",
            ^.role    := "tablist",
            <.li(
              ^.role    := "presentation",
              ^.`class` := "active",
              <.a(
                ^.href              := "#dependencies",
                ^.aria.controls     := "dependencies",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                s"Dependencies (${S.modules.length})"
              )
            ),
            <.li(
              ^.role := "presentation",
              <.a(
                ^.href              := "#repositories",
                ^.aria.controls     := "repositories",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                s"Repositories (${S.repositories.length})"
              )
            ),
            <.li(
              ^.role := "presentation",
              <.a(
                ^.href              := "#options",
                ^.aria.controls     := "options",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                "Options"
              )
            )
          ),
          <.div(
            ^.`class` := "tab-content",
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane active",
              ^.id      := "dependencies",
              modules((S.modules, S.editModuleIdx, backend))
            ),
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane",
              ^.id      := "repositories",
              repositories((S.repositories, S.editRepoIdx, backend))
            ),
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane",
              ^.id      := "options",
              options((S.options, backend))
            )
          )
        ),
        <.div(<.form(
          ^.onSubmit ==> backend.handleResolve,
          <.button(
            ^.`type`   := "submit",
            ^.id       := "resolveButton",
            ^.`class`  := "btn btn-lg btn-primary",
            ^.disabled := S.resolving,
            if (S.resolving) "Resolving..." else "Resolve"
          )
        )),
        <.div(
          ^.role := "tabpanel",
          ^.id   := "results",
          <.ul(
            ^.`class` := "nav nav-tabs",
            ^.role    := "tablist",
            ^.id      := "resTabs",
            <.li(
              ^.role := "presentation",
              ^.id   := "resResTab",
              <.a(
                ^.href              := "#resolution",
                ^.aria.controls     := "resolution",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                "Resolution"
              )
            ),
            <.li(
              ^.role := "presentation",
              ^.id   := "resLogTab",
              <.a(
                ^.href              := "#log",
                ^.aria.controls     := "log",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                "Log"
              )
            ),
            <.li(
              ^.role := "presentation",
              <.a(
                ^.href              := "#depgraph",
                ^.aria.controls     := "depgraph",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                "Graph"
              )
            ),
            <.li(
              ^.role := "presentation",
              <.a(
                ^.href              := "#deptreepanel",
                ^.aria.controls     := "deptreepanel",
                ^.role              := "tab",
                Attr("data-toggle") := "tab",
                "Tree"
              )
            )
          ),
          <.div(
            ^.`class` := "tab-content",
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane",
              ^.id      := "resolution",
              resolution((S.resolutionOpt, backend))
            ),
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane",
              ^.id      := "log",
              <.button(
                ^.`type`  := "button",
                ^.`class` := "btn btn-default",
                ^.onClick ==> backend.clearLog,
                "Clear"
              ),
              <.div(^.`class` := "well", <.ul(^.`class` := "log", S.log.map(e => <.li(e)).toTagMod))
            ),
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane",
              ^.id      := "depgraph",
              <.button(
                ^.`type`  := "button",
                ^.`class` := "btn btn-default",
                ^.onClick ==> backend.updateDepGraphBtn(
                  S.resolutionOpt.getOrElse(Resolution())
                ),
                "Redraw"
              ),
              <.div(^.id := "depgraphcanvas")
            ),
            <.div(
              ^.role    := "tabpanel",
              ^.`class` := "tab-pane",
              ^.id      := "deptreepanel",
              <.div(
                ^.`class` := "checkbox",
                <.label(
                  <.input.checkbox(
                    ^.onChange ==> backend.toggleReverseTree,
                    if (S.reverseTree) ^.checked := true else TagMod()
                  ),
                  "Reverse"
                )
              ),
              <.div(^.id := "deptree")
            )
          )
        )
      )
    }
    .build

}
