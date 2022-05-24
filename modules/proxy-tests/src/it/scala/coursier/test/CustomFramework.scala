package coursier.test

import coursier.bootstrap.launcher.proxy.SetupProxy
import utest.runner.Framework

class CustomFramework extends Framework {

  override def setup(): Unit =
    SetupProxy.setupAuthenticator(
      "localhost",
      "9083",
      "jack",
      "insecure",
      null,
      null,
      null,
      null,
      "localhost",
      "9084",
      "wrong",
      "nope"
    )

}
