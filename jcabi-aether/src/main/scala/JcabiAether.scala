import com.jcabi.aether.JcabiAetherExtended
import com.jcabi.aether.Repository
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.graph.Dependency
import org.sonatype.aether.collection.CollectRequest

import scala.collection.JavaConverters._
import java.io.File

object JcabiAether extends App {

  val scope = "runtime"

  val (splitArtifacts, malformed) = args.toList
    .map(_.split(":", 3).toSeq)
    .partition(_.length == 3)

  if (splitArtifacts.isEmpty) {
    Console.err.println("Usage: fetch artifacts...")
    sys exit 1
  }

  if (malformed.nonEmpty) {
    Console.err.println(s"Malformed artifacts:\n${malformed.map(_.mkString(":")).mkString("\n")}")
    sys exit 1
  }

  val artifacts = splitArtifacts.map{
    case Seq(org, name, version) =>
      new DefaultArtifact(org, name, "", "jar", version)
  }

  val local = new File("target/local-repository")
  val remotes = List(
    new RemoteRepository("maven-central", "default", "https://repo1.maven.org/maven2/")
  )

  val jcabi = new JcabiAetherExtended(remotes, local)

  val deps = jcabi.resolveSeveral(
    artifacts,
    scope
  )


  println(deps.map(_.toString).sorted.mkString("\n"))

}
