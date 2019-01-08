---
title: scala-native bootstrap
---

The `bootstrap` command can generate native executables via [scala-native](http://scala-native.org). This requires the corresponding scala-native app to publish its JARs, on Maven Central for example, and your environment to be [set up for scala-native](http://www.scala-native.org/en/latest/user/setup.html). One can then generate executables with a command like
```bash
$ ./coursier bootstrap \
    --native \
    io.get-coursier:echo_native0.3_2.11:1.0.2 \
    -o echo-native
[info] Linking (2354 ms)
[info] Discovered 1291 classes and 9538 methods
…
$ ./echo-native hey
hey
```

