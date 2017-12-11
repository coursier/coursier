scala_library(
  name = 'core',
  sources = rglobs('jvm/*.scala', 'shared/*.scala'),
  dependencies = [
    '3rdparty/jvm:fastParse',
    '3rdparty/jvm:scalaz-core',
    '3rdparty/jvm:jsoup',
    '3rdparty/jvm:jackson-module-scala',
    # TODO(wisechengyi) for some reason there is no compile error
    # and this is needed at runtime.
    '3rdparty/jvm:scala-xml',
   ]
)
