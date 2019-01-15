---
title: Website
---

The website relies on [docusaurus](https://docusaurus.io) and
[mdoc](https://scalameta.org/mdoc). Its sources are available
under [`doc/docs`](https://github.com/coursier/coursier/tree/master/doc/docs)
(page content, in Markdown) and
[`doc/website`](https://github.com/coursier/coursier/tree/master/doc/website)
(docusaurus configuration mostly).

Some of its logic is handled via [Ammonite](https://ammonite.io).

## Setup

### Ammonite

Ensure [Ammonite](https://ammonite.io) `1.6.x` is installed. Alternatively,
fetch it via
```bash
$ (echo "#!/usr/bin/env sh" && curl -L https://github.com/lihaoyi/Ammonite/releases/download/1.6.2/2.12-1.6.2) > amm
$ chmod +x amm
```
Then run `./amm` rather than just `amm` below.

### yarn / npm / npx

The website relies on yarn / npm / npx to fetch and run
[docusaurus](https://docusaurus.io). Ensure these are available on your
PATH.

## Batch mode

To generate the website once, run
```bash
$ amm scripts/site.sc --publishLocal true --npmInstall true
$ amm scripts/site.sc --yarnRunBuild true --relativize true
```

- `--publishLocal true` publishes locally the core and cache modules, so that they can be later picked by mdoc,
- `--npmInstall true` runs `npm install` from the `doc/website` directory, to install docusaurus in particular,
- `--yarnRunBuild true` runs docusaurus via `yarn run build` from `doc/website`, to have docusaurus generate the website,
- `--relativize true` relativizes links in the output of docusaurus, so that the
site doesn't require being accessed from a particular path.

Note that the first command,
`amm scripts/site.sc --publishLocal true --npmInstall true`, only needs to be
run once, unless you made or pulled changes in the sources of coursier, that
are used from the documentation.

You can then run
```bash
$ npx http-server doc/website/build/coursier
```
to browse the website. This command starts a website to browse the generated
documentation (its address should be printed in the console).

## Watch mode

To run the website while watching its sources (under `doc/docs`), run
```bash
$ amm scripts/site.sc --publishLocal true --npmInstall true
$ amm scripts/site.sc --yarnRunBuild true --watch true
```

- `--publishLocal true` publishes locally the core and cache modules, so that they can be later picked by mdoc,
- `--npmInstall true` runs `npm install` from the `doc/website` directory, to install docusaurus in particular,
- `--yarnRunBuild true` runs docusaurus via `yarn run start` from `doc/website`, to have docusaurus generate the website and watch for changes,
- `--watch true` runs mdoc in watch mode.

This runs both docusaurus and mdoc in watch mode. The former should open
a browser window, that automatically refreshes upon changes.

Like above, note that the first command  only needs to be run once, unless you
made or pulled changes in the sources of coursier, that are used from the
documentation.

