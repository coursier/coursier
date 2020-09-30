@echo off

@rem script intended for local use, customize to your local installation as needed

@rem You need to run "sbt cli/pack" prior to running this script.
@rem You also need to run a "sbt publishLocal", note the version it published,
@rem and pass it to this script like --version 2.0.0-...-SNAPSHOT

@rem sometimes needed, see https://github.com/oracle/graal/issues/2522
chcp 437

@rem this path may need to be customized, depending on your installation
@call "C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\VC\Auxiliary\Build\vcvars64.bat"

@call modules\cli\target\pack\bin\coursier.bat launch --jvm-index cs --jvm graalvm-java11:20.2.0 ammonite:2.0.4 -- launcher.sc generateNativeImage --output cs-dev %*
