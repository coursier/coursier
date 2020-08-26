package coursier.test

import utest.runner.Framework

class CustomFramework extends Framework {

  override def setup(): Unit =
    coursier.cache.CacheUrl.setupProxyAuth(Map(
      ("http", "localhost", "9083") -> ("jack", "insecure"),
      ("http", "localhost", "9084") -> ("wrong", "nope")
    ))

}
