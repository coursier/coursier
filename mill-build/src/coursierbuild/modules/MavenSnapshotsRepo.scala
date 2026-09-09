package coursierbuild.modules

import mill.*
import mill.javalib.CoursierModule

/** Adds the Maven Central snapshot repository to the ones dependencies are resolved from.
  *
  * Needed as long as `Deps.versions` points at a snapshot version, which isn't published on Maven
  * Central proper. `CoursierJavaModule` mixes it in, which covers most modules, and their test
  * modules along with them (those inherit `repositoriesTask` from their outer module). Mix it in
  * explicitly in the few modules that depend on the coursier ones without extending
  * `CoursierJavaModule`.
  */
trait MavenSnapshotsRepo extends CoursierModule {
  def repositories = super.repositories() ++ Seq(
    "central:maven-snapshots"
  )
}
