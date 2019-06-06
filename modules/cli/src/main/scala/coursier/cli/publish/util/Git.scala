package coursier.cli.publish.util

import java.io.File

object Git {

  def remoteUrl(dir: File): String = {
    // from https://github.com/sbt/sbt-git/blob/f8caf9365be380cf101e9605af159b5e7f842d0c/src/main/scala/com/typesafe/sbt/git/JGit.scala#L100
    scala.sys.process.Process(Seq("git", "ls-remote", "--get-url", "origin")).lineStream_!.head
  }

  def apply(dir: File): Option[(String, String)] = {

    // from https://github.com/sbt/sbt-git/blob/f8caf9365be380cf101e9605af159b5e7f842d0c/src/main/scala/com/typesafe/sbt/SbtGit.scala#L125-L130
    val user = """(?:[^@\/]+@)?"""
    val domain = """([^\/]+)"""
    val gitPath = """(.*)\.git\/?"""
    val unauthenticated = raw"""(?:git|https?|ftps?)\:\/\/$domain\/$gitPath""".r
    val ssh = raw"""ssh\:\/\/$user$domain\/$gitPath""".r
    val headlessSSH = raw"""$user$domain:$gitPath""".r

    remoteUrl(dir) match {
      case unauthenticated(domain0, repo) => Some((domain0,repo))
      case ssh(domain0, repo) => Some((domain0,repo))
      case headlessSSH(domain0, repo) => Some((domain0,repo))
      case _ => None
    }
  }

}
