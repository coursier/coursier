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
$ cs bootstrap ammonite:2.0.4 -o amm
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
