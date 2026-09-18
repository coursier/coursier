package mill.api

/** Lives in the `mill` package to access the `private[mill]` segments of a task */
object TaskSelector {

  /** The selector of a task, as accepted on the Mill command line, like
    * `core.jvm[2.13.18].test.testForked`
    */
  def render(task: Task.Named[?]): String =
    task.ctx.segments.render
}
