package coursier.maven

import coursier.core.{Configuration, Dependency, Project, Variant}

object WritePom {

  // format: off
  def project(proj: Project, packaging: Option[String]) = {

    def dependencyNode(config: Configuration, dep: Dependency) = {
      <dependency>
        <groupId>{dep.module.organization}</groupId>
        <artifactId>{dep.module.name}</artifactId>
        {
          if (dep.versionConstraint.asString.isEmpty)
            Nil
          else
            Seq(<version>{dep.versionConstraint.asString}</version>)
        }
        {
          if (config.isEmpty)
            Nil
          else
            Seq(<scope>{config.value}</scope>)
        }
      </dependency>
    }

    <project>
      // parent
      <groupId>{proj.module.organization}</groupId>
      <artifactId>{proj.module.name}</artifactId>
        {
          packaging
            .map(p => <packaging>{p}</packaging>)
            .toSeq
        }
      <description>{proj.info.description}</description>
      <url>{proj.info.homePage}</url>
      <version>{proj.version0.asString}</version>
      // licenses
      <name>{proj.module.name}</name>
      <organization>
        <name>{proj.module.name}</name>
        <url>{proj.info.homePage}</url>
      </organization>
      // SCM
      // developers
        {
          if (proj.dependencies0.isEmpty)
            Nil
          else
            <dependencies>
              {
                proj.dependencies0.map {
                  case (variant, dep) =>
                    val config = variant match {
                      case c: Variant.Configuration => c.configuration
                    }
                    dependencyNode(config, dep)
                }
              }
            </dependencies>
        }
        {
          if (proj.dependencyManagement.isEmpty)
            Nil
          else
            <dependencyManagement>
              <dependencies>
                {
                  proj.dependencyManagement.map {
                    case (config, dep) =>
                      dependencyNode(config, dep)
                  }
                }
              </dependencies>
            </dependencyManagement>
        }
      // properties
      // repositories
    </project>
  }
  // format: on

}
