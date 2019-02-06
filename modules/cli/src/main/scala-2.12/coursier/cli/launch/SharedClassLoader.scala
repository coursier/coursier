package coursier.cli.launch

import java.net.{URL, URLClassLoader}

final class SharedClassLoader(
  urls: Array[URL],
  parent: ClassLoader,
  isolationTargets: Array[String]
) extends URLClassLoader(urls, parent) {

  /**
    * Mostly deprecated.
    *
    * Applications wanting to access an isolated `ClassLoader` should inspect the hierarchy of
    * loaders, and look into each of them for this method, by reflection. Then they should
    * call it (still by reflection), and look for an agreed in advance target in it. If it is found,
    * then the corresponding `ClassLoader` is the one with isolated dependencies.
    *
    * As a substitute, users should call classOf[…].getClassLoader on a class they know is in a shared
    * classloader, to find specific classloaders.
    */
  def getIsolationTargets: Array[String] = isolationTargets

}
