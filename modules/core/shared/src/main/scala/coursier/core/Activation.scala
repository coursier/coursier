package coursier.core

import dataclass.data

// Maven-specific
@data class Activation(
  properties: Seq[(String, Option[String])],
  os: Activation.Os,
  jdk: Option[Either[VersionInterval, Seq[Version]]]
) {

  def isEmpty: Boolean = properties.isEmpty && os.isEmpty && jdk.isEmpty

  def isActive(
    currentProperties: Map[String, String],
    osInfo: Activation.Os,
    jdkVersion: Option[Version]
  ): Boolean = {
    def fromProperties = properties.forall {
      case (name, _) if name.startsWith("!") =>
        currentProperties.get(name.drop(1)).isEmpty
      
      case (name, None) => 
        currentProperties.contains(name)

      // https://maven.apache.org/guides/introduction/introduction-to-profiles.html
      // if the value starts with !, this property activates if either
      // a) the property is missing completely
      // b) it's value is NOT equal to expected
      case (name, Some(expected)) if expected.startsWith("!") => 
        currentProperties.get(name).fold(true)(found => found != expected.drop(1))
      
      case (name, Some(expected)) => 
        currentProperties.get(name).contains(expected)
    }

    def fromOs = os.isActive(osInfo)

    def fromJdk = jdk.forall {
      case Left(itv) =>
        jdkVersion.exists(itv.contains)
      case Right(versions) =>
        // Per the Maven doc (https://maven.apache.org/guides/introduction/introduction-to-profiles.html),
        // we should only check if the JDK version starts with any of the passed versions.
        // We do things a little more strictly here, enforcing either the exact same JDK
        // version, or a JDK version starting with one of the passed versions plus '.',
        // so that '1.8' matches JDK versions '1.8' or '1.8.1', but not '1.80'…
        jdkVersion.exists(v => versions.exists(v0 => v == v0 || v.repr.startsWith(v0.repr + ".")))
    }

    !isEmpty && fromProperties && fromOs && fromJdk
  }
}

object Activation {

  @data class Os(
    arch: Option[String],
    families: Set[String],
    name: Option[String],
    version: Option[String] // FIXME Could this be an interval?
  ) {
    def isEmpty: Boolean =
      arch.isEmpty && families.isEmpty && name.isEmpty && version.isEmpty

    def archMatch(current: Option[String]): Boolean =
      arch.forall(current.toSeq.contains) || {
        // seems required by org.nd4j:nd4j-native:0.5.0
        arch.toSeq.contains("x86-64") && current.toSeq.contains("x86_64")
      }

    def isActive(osInfo: Os): Boolean =
      archMatch(osInfo.arch) &&
        families.forall { f =>
          if (Os.knownFamilies(f))
            osInfo.families.contains(f)
          else
            osInfo.name.exists(_.contains(f))
        } &&
        name.forall(osInfo.name.toSeq.contains) &&
        version.forall(osInfo.version.toSeq.contains)
  }

  object Os {
    val empty = Os(None, Set(), None, None)

    // below logic adapted from https://github.com/sonatype/plexus-utils/blob/f2beca21c75084986b49b3ab7b5f0f988021dcea/src/main/java/org/codehaus/plexus/util/Os.java
    // brought in https://github.com/coursier/coursier/issues/341 by @eboto

    private val standardFamilies = Set(
      "windows",
      "os/2",
      "netware",
      "mac",
      "os/400",
      "openvms"
    )

    private[Os] val knownFamilies = standardFamilies ++ Seq(
      "dos",
      "tandem",
      "unix",
      "win9x",
      "z/os"
    )

    def families(name: String, pathSep: String): Set[String] = {

      var families = standardFamilies.filter(f => name.indexOf(f) >= 0)

      if (pathSep == ";" && name.indexOf("netware") < 0)
        families += "dos"

      if (name.indexOf("nonstop_kernel") >= 0)
        families += "tandem"

      if (pathSep == ":" && name.indexOf("openvms") < 0 && (name.indexOf("mac") < 0 || name.endsWith("x")))
        families += "unix"

      if (name.indexOf("windows") >= 0 && (name.indexOf("95") >= 0 || name.indexOf("98") >= 0 || name.indexOf("me") >= 0 || name.indexOf("ce") >= 0))
        families += "win9x"

      if (name.indexOf("z/os") >= 0 || name.indexOf("os/390") >= 0)
        families += "z/os"

      families
    }

    def fromProperties(properties: Map[String, String]): Os = {
      val name = properties.get("os.name").map(_.toLowerCase)

      Os(
        properties.get("os.arch").map(_.toLowerCase),
        (for (n <- name; sep <- properties.get("path.separator"))
          yield families(n, sep)).getOrElse(Set()),
        name,
        properties.get("os.version").map(_.toLowerCase)
      )
    }
  }

  val empty = Activation(Nil, Os.empty, None)
}
