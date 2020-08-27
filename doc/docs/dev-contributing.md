---
title: Contributing
---

See the [up-to-date list of contributors on
GitHub](https://github.com/coursier/coursier/graphs/contributors).

Don't hesitate to pick an issue to contribute, and / or ask for help for how to
proceed on the [Gitter channel](https://gitter.im/coursier/coursier).

## Setting the project up

  - Clone down the [Coursier Repo](https://github.com/coursier/coursier)
  - Change directory in the Coursier root and issue a
      `git submodule update --init`  which will clone down the necessary
      submodules for the project.
  - Use the local `./sbt` rather than your machine sbt as it behaves slightly
      different than the default sbt launcher. You get the following compile
      error if you use the machine sbt: `not found: value sbtCoursierVersion`.

##  Ensuring binary compatibility

Keep in mind while developing that during CI a `compatibilityCheck` will happen
to ensure binary compatibility is not being broken. If these errors are seen you
may have to add some extra methods that
[`mima`](https://github.com/lightbend/mima) points out to you in the logs. This
project uses the [data-class](https://github.com/alexarchambault/data-class)
library to make this a bit easier.

If you notice a warning from mima that isn't relevant, for example if  you
changed a private method, you can add the filter printed by
`mimaReportBinaryIssues` into `project/mima.scala` to filter it out.
