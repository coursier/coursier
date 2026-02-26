package coursier.cli.setup

import coursier.util.Task

import java.io.PrintStream

trait SetupStep {
  def banner: String
  def task: Task[Unit]
  def tryRevert: Task[Unit]

  final def fullTask(out: PrintStream): Task[Unit] =
    for {
      _ <- Task.delay(out.println(banner))
      _ <- task
      _ <- Task.delay(out.println())
    } yield ()
}
