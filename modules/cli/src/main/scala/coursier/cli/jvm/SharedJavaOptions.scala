package coursier.cli.jvm

import caseapp.Recurse

final case class SharedJavaOptions(
  jvm: Option[String] = None,
  jvmDir: Option[String] = None,
  systemJvm: Option[Boolean] = None,
  localOnly: Boolean = false,
  update: Boolean = false
)
