package coursierbuild.modules

import mill.*
import mill.javalib.CoursierModule

/** Adds the Maven Central snapshot repository to the ones dependencies are resolved from.
  *
  * Needed as long as `Deps.versions` or `Deps.dependency` point at snapshot versions, which aren't
  * published on Maven Central proper. `CoursierJavaModule` mixes it in, which covers most modules.
  * Mix it in explicitly in the few modules that depend on the coursier ones without extending
  * `CoursierJavaModule`.
  */
trait MavenSnapshotsRepo extends CoursierModule {
  def repositories = super.repositories() ++ MavenSnapshotsRepo.repositories
}

object MavenSnapshotsRepo {

  /** The repositories the dependencies of the published modules need, on top of Central
    *
    * Also passed to `cs bootstrap` when generating JAR launchers, which resolves those dependencies
    * from the published POMs.
    */
  def repositories: Seq[String] = Seq("central:maven-snapshots")
}
