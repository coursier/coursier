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
$ amm website.sc generate
```

You can then run
```bash
$ npx http-server doc/website/build/coursier
```
to browse the website. This command starts a website to browse the generated
documentation (its address should be printed in the console).

## Watch mode

To run the website while watching its sources (which live under `doc/docs`), run
```bash
$ amm website.sc watch
```

This runs both docusaurus and mdoc in watch mode. The former should open
a browser window, that automatically refreshes upon changes.
