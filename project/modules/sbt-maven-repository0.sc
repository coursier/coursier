import $file.^.deps, deps.Deps
import $file.^.shading, shading.Shading
import $file.shared,
  shared.{CoursierPublishModule, CsCrossJvmJsModule, CsMima, CsModule, commitHash}

trait SbtMavenRepository extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName = "coursier-sbt-maven-repository"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass
  )
}
trait SbtMavenRepositoryJvmBase extends SbtMavenRepository with CsMima
