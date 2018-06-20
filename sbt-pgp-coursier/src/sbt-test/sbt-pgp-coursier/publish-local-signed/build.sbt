scalaVersion := "2.12.3"

// seen in https://github.com/sbt/sbt-pgp/blob/431c0a50fc5e91b881ebb154f22cc6a0b209be10/pgp-plugin/src/sbt-test/sbt-pgp/skip/build.sbt
credentials.in(GlobalScope) := Seq(Credentials("", "pgp", "", "test password"))
pgpSecretRing := baseDirectory.value / "secring.pgp"
pgpPublicRing := baseDirectory.value / "pubring.pgp"

// seems required by sbt-pgp 1.1.1, even to publish locally
publishTo := Some("foo" at "https://f.oo")
