@echo off

@rem You need to run "sbt cli/pack" prior to running this script.
@rem You also need to run a "sbt publishLocal", note the version it published,
@rem and pass it to this script like --version 2.0.0-...-SNAPSHOT

@call modules\cli\target\pack\bin\coursier.bat launch --jvm graalvm:19.3 ammonite:2.0.4 -- launcher.sc generateNativeImage --output cs-dev %*
