package coursier.test

import coursier.proxy.SetupProxy
import utest.runner.Framework

class CustomFramework extends Framework {

  override def setup(): Unit =
    SetupProxy.setupAuthenticator(
      "http",
      "localhost",
      "9083",
      "jack",
      "insecure",
      null,
      null,
      null,
      null,
      null,
      "http",
      "localhost",
      "9084",
      "wrong",
      "nope"
    )

}
