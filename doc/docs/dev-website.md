---
title: Website
---

The website relies on [docusaurus](https://docusaurus.io) and
[mdoc](https://scalameta.org/mdoc). Its sources are available
under [`doc/docs`](https://github.com/coursier/coursier/tree/master/doc/docs)
(page content, in Markdown) and
[`doc/website`](https://github.com/coursier/coursier/tree/master/doc/website)
(docusaurus configuration mostly).

Some of its logic is handled via the
[mill](https://www.lihaoyi.com/mill) build tool, even though coursier
itself is still built via [sbt](https://www.scala-sbt.org).

## Setup

### mill

Ensure [mill](https://www.lihaoyi.com/mill) `0.3.5` is installed. Alternatively,
fetch it via
```bash
$ curl -Lo mill https://github.com/lihaoyi/mill/releases/download/0.3.5/0.3.5
$ chmod +x mill
```
Then run `./mill` rather than just `mill` below.

### yarn / npm / npx

The website relies on yarn / npm / npx to fetch and run
[docusaurus](https://docusaurus.io). Ensure these are available on your
PATH.

## Batch mode

To generate the website once, run
```bash
$ mill -i all doc.publishLocal doc.relativize doc.httpServerBg
```

- `publishLocal` publishes locally the core and cache modules, so that they can be later picked by mdoc,
- `relativize` runs mdoc, then docusaurus via `yarn run build` from `doc/website`, then relativizes the output of docusaurus (under `doc/website/build`),
- `httpServerBg` runs `npx http-server doc/website/build/coursier`, which starts a website to browse the generated documentation (its address should be printed in the console).

## Watch mode

To run the website while watching its sources (under `doc/docs`), run
```bash
$ mill -i all doc.publishLocal doc.mdoc doc.yarnStartBg doc.mdocWatch
```

- `publishLocal` publishes locally the core and cache modules, so that they can be later picked by mdoc,
- `mdoc` runs mdoc once,
- `yarnStartBg` runs `yarn run start` from the `doc/website` directory in the background, which starts docusaurus in watch mode,
- `mdocWatch` runs mdoc in watch mode.

`yarnStartBg` should open a browser window at the website address, that automatically refreshes when the sources change.
