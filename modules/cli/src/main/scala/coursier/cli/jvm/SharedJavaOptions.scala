package coursier.cli.jvm

import caseapp.Recurse
import coursier.cli.options.{CacheOptions, OutputOptions}

final case class SharedJavaOptions(
  jvm: Option[String] = None,
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions()
)
