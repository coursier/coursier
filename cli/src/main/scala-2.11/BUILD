scala_library(
  name = 'cli',
  sources = rglobs('*.scala'),
  dependencies = [
  	'3rdparty/jvm:argonaut-shapeless',
  	'3rdparty/jvm:caseapp',
  	'core:core',
  	'cache/src/main/scala:cache',
    'extra/src/main/scala/coursier/extra:extra',
    'extra/src/main/scala-2.11/coursier/extra:native',
  ]
)

jvm_binary(name='coursier-cli',
  basename='coursier-cli',
  dependencies=[
   ':cli'
  ],
  main='coursier.cli.Coursier'
)
