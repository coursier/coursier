package coursier.launcher

import java.util.jar.JarFile
import java.util.regex.Pattern
import dataclass.data

sealed abstract class MergeRule extends Product with Serializable

object MergeRule {
  sealed abstract class PathRule extends MergeRule {
    def path: String
  }

  @data class Exclude(path: String) extends PathRule
  @data class ExcludePattern(path: Pattern) extends MergeRule

  object ExcludePattern {
    def apply(s: String): ExcludePattern =
      ExcludePattern(Pattern.compile(s))
  }

  // TODO Accept a separator: Array[Byte] argument in these
  // (to separate content with a line return in particular)
  @data class Append(path: String) extends PathRule
  @data class AppendPattern(path: Pattern) extends MergeRule

  object AppendPattern {
    def apply(s: String): AppendPattern =
      AppendPattern(Pattern.compile(s))
  }

  val default = Seq(
    MergeRule.Append("reference.conf"),
    MergeRule.AppendPattern("META-INF/services/.*"),
    MergeRule.Exclude("log4j.properties"),
    MergeRule.Exclude(JarFile.MANIFEST_NAME),
    MergeRule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    MergeRule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    MergeRule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
  )
}
