package coursier.cli.setup

import java.io.PrintStream

import coursier.util.Task

trait SetupStep {
  def banner: String
  def task: Task[Unit]

  final def fullTask(out: PrintStream): Task[Unit] =
    for {
      _ <- Task.delay(out.println("  " + banner))
      _ <- task
      _ <- Task.delay(out.println())
    } yield ()
}
