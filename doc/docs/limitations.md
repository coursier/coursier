---
title: Limitations
---

*Direct former README import (possibly not up-to-date)*

#### Ivy support is poorly tested

The minimum was made for SBT plugins to be resolved fine (including dependencies
between plugins, the possibility that some of them come from Maven repositories,
with a peculiarities, classifiers - sources, javadoc - should be fine too).
So it is likely that projects relying more heavily
on Ivy features could run into the limitations of the current implementation.

Any issue report related to that, illustrated with public Ivy repositories
if possible, would be greatly appreciated.

#### *Important*: SBT plugin might mess with published artifacts

SBT seems to require the `update` command to generate a few metadata files
later used by `publish`. If ever there's an issue with these, this might
add discrepancies in the artifacts published with `publish` or `publishLocal`.
Should you want to use the coursier SBT plugin while publishing artifacts at the
same time, I'd recommend an extreme caution at first, like manually inspecting
the metadata files and compare with previous ones, to ensure everything's fine.

coursier publishes its artifacts with its own plugin enabled since version
`1.0.0-M2` though, without any apparent problem.

#### No wait on locked file

If ever resolution or artifact downloading stumbles upon a locked metadata or
artifact in the cache, it will just fail, instead of waiting for the lock to be freed.

#### Also

Plus the inherent amount of bugs arising in a young project :-)
