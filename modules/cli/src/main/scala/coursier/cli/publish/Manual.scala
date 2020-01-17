package coursier.cli.publish

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

import coursier.publish.fileset.{FileSet, Path}
import coursier.cli.publish.params.{MetadataParams, SinglePackageParams}
import coursier.core.{ModuleName, Organization}
import coursier.publish.{Content, Pom}

object Manual {

  private def pomModuleVersion(params: SinglePackageParams, metadata: MetadataParams, now: Instant): Either[PublishError.InvalidArguments, (Content, Organization, ModuleName, String)] =
    (metadata.organization, metadata.name, metadata.version) match {
      case (Some(org), Some(name), Some(ver)) =>

        val content = params.pomOpt match {
          case Some(path) =>
            Content.File(path)
          case None =>
            val pomStr = Pom.create(
              org, name, ver, dependencies = metadata.dependencies.getOrElse(Nil).map {
                case (org0, name0, ver0) =>
                  (org0, name0, ver0, None)
              }
            )
            Content.InMemory(now, pomStr.getBytes(StandardCharsets.UTF_8))
        }

        Right((content, org, name, ver))

      case (orgOpt, nameOpt, verOpt) =>
        params.pomOpt match {
          case None =>
            Left(new PublishError.InvalidArguments(s"Either specify organization / name / version, or pass a POM file."))
          case Some(path) =>
            val s = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

            val elem = scala.xml.XML.loadString(s) // can throw…
          val xml = coursier.core.compatibility.xmlFromElem(elem)

            val pomOrError =
              for {
                _ <- (if (xml.label == "project") Right(()) else Left("Project definition not found"))
                proj <- coursier.maven.Pom.project(xml)
              } yield proj

            pomOrError match {
              case Left(err) => Left(new PublishError.InvalidArguments(s"Error parsing $path: $err"))
              case Right(proj) =>
                val org = orgOpt.getOrElse(proj.module.organization)
                val name = nameOpt.getOrElse(proj.module.name)
                val ver = verOpt.getOrElse(proj.version)
                val content =
                  if (metadata.isEmpty)
                    Content.File(path)
                  else {

                    var elem0 = elem
                    elem0 = orgOpt.fold(elem0)(Pom.overrideOrganization(_, elem0))
                    elem0 = nameOpt.fold(elem0)(Pom.overrideModuleName(_, elem0))
                    elem0 = verOpt.fold(elem0)(Pom.overrideVersion(_, elem0))

                    val pomStr = Pom.print(elem0)
                    Content.InMemory(now, pomStr.getBytes(StandardCharsets.UTF_8))
                  }

                Right((content, org, name, ver))
            }
        }
    }

  def manualPackageFileSet(params: SinglePackageParams, metadata: MetadataParams, now: Instant): Either[PublishError.InvalidArguments, FileSet] =
    pomModuleVersion(params, metadata, now).map {
      case (pom, org, name, ver) =>

        val dir = Path(org.value.split('.').toSeq ++ Seq(name.value, ver))

        val jarOpt = params
          .jarOpt
          .map { path =>
            (dir / s"${name.value}-$ver.jar", Content.File(path))
          }

        val artifacts = params
          .artifacts
          .map {
            case (classifier, ext, path) =>
              val suffix =
                if (classifier.isEmpty) ""
                else "-" + classifier.value
              (dir / s"${name.value}-$ver$suffix.${ext.value}", Content.File(path))
          }

        FileSet(
          Seq((dir / s"${name.value}-$ver.pom", pom)) ++
            jarOpt.toSeq ++
            artifacts
        )
    }

}
