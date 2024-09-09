package coursier.web

import coursier.cache.{AlwaysDownload, CacheLogger}
import coursier.core.{Dependency, Module, Repository, Resolution, ResolutionProcess}
import coursier.maven.MavenRepository
import coursier.util.{Artifact, EitherT, Gather, Task}
import coursier.util.StringInterpolators._
import japgolly.scalajs.react._
import org.scalajs.dom

import scala.scalajs.js
import scala.util.{Failure, Success}
import js.Dynamic.{global => g}

final class Backend($ : BackendScope[_, State]) {

  def fetch(
    repositories: Seq[Repository],
    fetch: Repository.Fetch[Task]
  ): ResolutionProcess.Fetch[Task] = {

    val fetch0: Repository.Fetch[Task] = { a =>
      if (a.url.endsWith("/"))
        // don't fetch directory listings
        EitherT[Task, String, String](Task.point(Left("")))
      else
        fetch(a)
    }

    modVers =>
      Gather[Task].gather(
        modVers.map { case (module, version) =>
          ResolutionProcess.fetchOne(repositories, module, version, fetch, Nil)
            .run
            .map((module, version) -> _)
        }
      )
  }

  def updateDepGraph(resolution: Resolution) = {
    println("Rendering canvas")

    val graph = js.Dynamic.newInstance(Dracula.Graph)()

    var nodes = Set.empty[String]
    def addNode(name: String) =
      if (!nodes(name)) {
        graph.addNode(name)
        nodes += name
      }

    def repr(dep: Dependency) =
      Seq(
        dep.module.organization,
        dep.module.name,
        dep.configuration
      ).mkString(":")

    for {
      (dep, parents) <- resolution
        .reverseDependencies
        .toList
      from = repr(dep)
      _    = addNode(from)
      parDep <- parents
      to = repr(parDep)
      _  = addNode(to)
    } graph.addEdge(from, to)

    val layouter = js.Dynamic.newInstance(Dracula.Layout.Spring)(graph)
    layouter.layout()

    val width = g.jQuery("#dependencies")
      .width()
    val height = g.jQuery("#dependencies")
      .height()
      .asInstanceOf[Int]
      .max(400)

    println(s"width: $width, height: $height")

    g.jQuery("#depgraphcanvas")
      .html("") // empty()

    val renderer = js.Dynamic.newInstance(Dracula.Renderer.Raphael)(
      "#depgraphcanvas",
      graph,
      width,
      height
    )
    renderer.draw()
    println("Rendered canvas")
  }

  def updateDepGraphBtn(resolution: Resolution)(e: facade.SyntheticEvent[_]) = CallbackTo[Unit] {
    updateDepGraph(resolution)
  }

  def updateTree(resolution: Resolution, target: String, reverse: Boolean): Unit = {

    val minDependencies = resolution.minDependencies

    lazy val reverseDeps = {
      var m = Map.empty[Module, Seq[Dependency]]

      for {
        dep   <- minDependencies
        trDep <- resolution.dependenciesOf(dep)
      } m += trDep.module -> (m.getOrElse(trDep.module, Nil) :+ dep)

      m
    }

    def tree(dep: Dependency): js.Dictionary[js.Any] =
      js.Dictionary(Seq(
        "text" -> (s"${dep.module}": js.Any)
      ) ++ {
        val deps =
          if (reverse) reverseDeps.getOrElse(dep.module, Nil) else resolution.dependenciesOf(dep)
        if (deps.isEmpty) Seq()
        else Seq("nodes" -> js.Array(deps.map(tree): _*))
      }: _*)

    println(
      minDependencies
        .toList
        .map(tree)
        .map(js.JSON.stringify(_))
    )
    g.jQuery(target)
      .treeview(js.Dictionary("data" -> js.Array(minDependencies.toList.map(tree): _*)))
  }

  def resolve(action: => Unit = ()): CallbackTo[Unit] = {

    g.$("#resLogTab a:last").tab("show")
    $.modState(_.copy(resolving = true, log = Nil)).runNow()

    val logger: CacheLogger = new CacheLogger {
      override def downloadingArtifact(url: String, artifact: Artifact) = {
        println(s"<- $url")
        $.modState(s => s.copy(log = s"<- $url" +: s.log)).runNow()
      }
      override def downloadedArtifact(url: String, success: Boolean) = {
        println(s"-> $url")
        val extra =
          if (success) ""
          else " (failed)" // FIXME Have CacheLogger be passed more details in case of error
        $.modState(s => s.copy(log = s"-> $url$extra" +: s.log)).runNow()
      }
    }

    $.state.map { s =>

      def task = {
        val res = Resolution()
          .withRootDependencies(s.modules)
          .withFilter(Some(dep => s.options.followOptional || !dep.optional))
        ResolutionProcess(res).run(
          fetch(s.repositories.map { case (_, repo) => repo }, AlwaysDownload(logger).fetch),
          100
        )
      }

      implicit val ec = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      task.map { res: Resolution =>
        $.modState { s =>
          updateDepGraph(res)
          updateTree(res, "#deptree", reverse = s.reverseTree)

          s.copy(
            resolutionOpt = Some(res),
            resolving = false
          )
        }.runNow()

        g.$("#resResTab a:last")
          .tab("show")
      }.future()(ec).onComplete {
        case Success(_) =>
        case Failure(t) =>
          println(s"Caught exception: $t")
          println(t.getStackTrace.map("  " + _ + "\n").mkString)
      }

      ()
    }
  }
  def handleResolve(e: facade.SyntheticEvent[_]) = {

    val c = CallbackTo[Unit] {
      println(s"Resolving")
      e.preventDefault()
      g.jQuery("#results").css("display", "block")
    }

    c.flatMap { _ =>
      resolve()
    }
  }

  def clearLog(e: facade.SyntheticEvent[_]) =
    $.modState(_.copy(log = Nil))

  def toggleReverseTree(e: facade.SyntheticEvent[_]) =
    $.modState { s =>
      for (res <- s.resolutionOpt)
        updateTree(res, "#deptree", reverse = !s.reverseTree)
      s.copy(reverseTree = !s.reverseTree)
    }

  def editModule(idx: Int)(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(_.copy(editModuleIdx = idx))
  }

  def removeModule(idx: Int)(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(s =>
      s.copy(
        modules = s.modules
          .zipWithIndex
          .filter(_._2 != idx)
          .map(_._1)
      )
    )
  }

  def updateModule(
    moduleIdx: Int,
    update: (Dependency, String) => Dependency
  )(e: facade.SyntheticEvent[dom.raw.HTMLInputElement]) =
    if (moduleIdx >= 0) {
      e.persist()
      $.modState { state =>
        val dep = state.modules(moduleIdx)
        state.copy(
          modules = state.modules
            .updated(moduleIdx, update(dep, e.target.value))
        )
      }
    }
    else
      CallbackTo.pure(())

  def addModule(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState { state =>
      val modules = state.modules :+ Dependency(Module(org"", name"", Map.empty), "")
      println(s"Modules:\n${modules.mkString("\n")}")
      state.copy(
        modules = modules,
        editModuleIdx = modules.length - 1
      )
    }
  }

  def editRepo(idx: Int)(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(_.copy(editRepoIdx = idx))
  }

  def removeRepo(idx: Int)(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState(s =>
      s.copy(
        repositories = s.repositories
          .zipWithIndex
          .filter(_._2 != idx)
          .map(_._1)
      )
    )
  }

  def moveRepo(idx: Int, up: Boolean)(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState { s =>
      val idx0 = if (up) idx - 1 else idx + 1
      val n    = s.repositories.length

      if (idx >= 0 && idx0 >= 0 && idx < n && idx0 < n) {
        val a = s.repositories(idx)
        val b = s.repositories(idx0)

        s.copy(
          repositories = s.repositories
            .updated(idx, b)
            .updated(idx0, a)
        )
      }
      else
        s
    }
  }

  def updateRepo(
    repoIdx: Int,
    update: ((String, MavenRepository), String) => (String, MavenRepository)
  )(e: facade.SyntheticEvent[dom.raw.HTMLInputElement]) =
    if (repoIdx >= 0)
      $.modState { state =>
        val repo = state.repositories(repoIdx)
        state.copy(
          repositories = state.repositories
            .updated(repoIdx, update(repo, e.target.value))
        )
      }
    else
      CallbackTo.pure(())

  def addRepo(e: facade.SyntheticEvent[_]) = {
    e.preventDefault()
    $.modState { state =>
      val repositories = state.repositories :+ ("" -> MavenRepository(""))
      println(s"Repositories:\n${repositories.mkString("\n")}")
      state.copy(
        repositories = repositories,
        editRepoIdx = repositories.length - 1
      )
    }
  }

  def enablePopover(e: facade.SyntheticMouseEvent[_]) = CallbackTo[Unit] {
    g.$("[data-toggle='popover']")
      .popover()
  }

  object options {
    def toggleOptional(e: facade.SyntheticEvent[_]) =
      $.modState(s =>
        s.copy(
          options = s.options
            .copy(followOptional = !s.options.followOptional)
        )
      )
  }
}
