@call "C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
cs launch ammonite:2.1.4-11-307f3d8 --scala 2.12.12 --jvm graalvm-ce-java11:20.1.0 -- launcher.sc generateNativeImage --version 2.0-SNAPSHOT --output cs-test
