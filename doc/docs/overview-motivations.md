---
title: Motivations
---

In contrast to libraries out there allowing to interact with Maven or Ivy
repositories, coursier aims at making it easier to download things from
Maven or Ivy repositories, foster their use beyond just downloading dependencies
in build tools, and allow to build reliable things on top of them.

It puts a great emphasis on downloading things from repositories as fast as
one's bandwidth allows, while not requiring retaining global locks of any
kind during downloads. Its [CLI](cli-overview.md) illustrates how one can
rely on Maven or Ivy repositories to launch applications or have a glance at
packages' transitive dependencies. Its [API](api.md) follows functional
programming principles, aiming at making it easy to add dependency resolution
capabilities to one's applications and libraries.
