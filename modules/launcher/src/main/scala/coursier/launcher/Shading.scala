package coursier.launcher

import java.io.File
import java.nio.file.Path

import com.eed3si9n.jarjar.{PatternElement, Rule => JarjarRule}
import com.eed3si9n.jarjar.JJProcessor
import com.eed3si9n.jarjar.misplaced.MisplacedClassProcessorFactory
import com.eed3si9n.jarjar.util.CoursierJarProcessor

/** Class relocation ("shading"), implemented on top of
  * [[https://github.com/eed3si9n/jarjar-abrams jarjar-abrams]], the same way
  * [[https://github.com/coursier/sbt-shading sbt-shading]] does it.
  */
object Shading {

  private def jarjarRule(pattern: String, result: String): JarjarRule = {
    val rule = new JarjarRule
    rule.setPattern(pattern)
    rule.setResult(result)
    rule
  }

  def jarjarRule(rule: ShadingRule): JarjarRule =
    jarjarRule(rule.pattern, rule.result)

  /** Reads all of `inputs`, relocates classes according to `rules`, and writes the result to
    * `output`.
    *
    * Classes whose names (and references to them) match a rule pattern are renamed accordingly.
    * Several input JARs can be passed at once, in which case they are merged into the output (the
    * first occurrence of each entry wins).
    */
  def shadeJars(
    inputs: Seq[File],
    output: Path,
    rules: Seq[ShadingRule],
    verbose: Boolean = false
  ): Unit = {
    // .toList so that this is an immutable.Seq, as required by JJProcessor on Scala 2.12
    val jarjarRules: List[PatternElement] = rules.map(jarjarRule).toList
    val processor = new JJProcessor(
      jarjarRules,
      verbose,
      false,
      MisplacedClassProcessorFactory.Strategy.FATAL.toString
    )
    CoursierJarProcessor.run(inputs.toArray, output.toFile, processor, true)
  }
}
