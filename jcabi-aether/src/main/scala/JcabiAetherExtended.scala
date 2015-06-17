package com.jcabi.aether

import org.sonatype.aether.artifact.Artifact
import org.sonatype.aether.graph.Dependency
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.filter.DependencyFilterUtils
import org.sonatype.aether.collection.CollectRequest
import org.sonatype.aether.resolution.DependencyRequest

import java.io.File
import scala.collection.JavaConverters._

class JcabiAetherExtended(repos: Seq[RemoteRepository],
                          fileRepo: File) extends JcabiAether(repos.asJava, fileRepo) {

  def resolveSeveral(artifacts: Seq[Artifact], scope: String): List[Artifact] = {
    val filter = DependencyFilterUtils.classpathFilter(scope)
    assert(filter != null)

    val crq = new CollectRequest(
      artifacts.map(new Dependency(_, scope)).asJava,
      null,
      remotes.map(_.remote()).toList.asJava
    )

    val system = new RepositorySystemBuilder().build()

    fetch(system, session(system), new DependencyRequest(crq, filter)).asScala.toList
  }

}

