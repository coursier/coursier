---
title: Motivations
---

*Direct former README import (possibly not up-to-date)*

The current state of dependency management in Scala suffers several flaws, that prevent applications to fully
profit from and rely on dependency management. Coursier aims at addressing these by making it easy to:
- resolve / download dependencies programmatically,
- launch applications distributed via Maven / Ivy artifacts from the command-line,
- work offline with artifacts,
- sandbox dependency management between projects.

As its [API](quick-start-api.md) illustrates, getting artifacts of dependencies is just a matter of specifying these along
with a few repositories. You can then straightforwardly get the corresponding artifacts, easily getting
precise feedback about what goes on during the resolution.

Launching an application distributed via Maven artifacts is just a command away with the [launcher](cli-launch.md) of coursier.
In most cases, just specifying the corresponding main dependency is enough to launch the corresponding application.

If all your dependencies are in cache, chances are coursier will not even try to connect to remote repositories. This
also applies to snapshot dependencies of course - these are only updated on demand, not getting constantly in your way
like is currently the case by default with SBT.

When using coursier from the command-line or via its SBT plugin, sandboxing is just one command away. Just do
`export COURSIER_CACHE="$(pwd)/.coursier-cache"`, and the cache will become `.coursier-cache` from the current
directory instead of the default global `~/.coursier/cache/v1`. This allows for example to quickly inspect the content
of the cache used by a particular project, in case you have any doubt about what's in it.
