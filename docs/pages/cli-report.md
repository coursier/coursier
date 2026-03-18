# JSON report

The `fetch` command of coursier CLI can generate a report, in JSON format, of the dependencies it fetched.

This report can be used by other tools. Most notably, Bazel's
[rules_jvm_external](https://github.com/bazel-contrib/rules_jvm_external) relies on coursier
to manage JVM dependencies. Before it, coursier's JSON reports were generated and used
[on the Twitter infrastructure via Pants](https://v1.pantsbuild.org/coursier_migration.html).
