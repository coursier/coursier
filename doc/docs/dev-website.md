---
title: Website
---

The website relies on [docusaurus](https://docusaurus.io) and
[mdoc](https://scalameta.org/mdoc). Its sources are available
under [`doc/docs`](https://github.com/coursier/coursier/tree/master/doc/docs)
(page content, in Markdown) and
[`doc/website`](https://github.com/coursier/coursier/tree/master/doc/website)
(docusaurus configuration mostly).

## Setup

### Node / yarn / npm / npx

The website relies on yarn / npm / npx to fetch and run
[docusaurus](https://docusaurus.io) 3. Ensure these are available on your
PATH. Docusaurus 3 requires Node 20 or later.

### Past versions

The docs of the past versions live in the
[coursier/versioned-docs](https://github.com/coursier/versioned-docs) repository.
To include them in the website, run
```bash
$ ./mill -i doc.copyVersionedData
```

This copies them under `doc/website`, and converts the versions still in the
Docusaurus 1 format to the Docusaurus 3 one along the way.

## Batch mode

To generate the website once, run
```bash
$ ./mill -i doc.generate --npm-install --yarn-run-build
```

You can then run
```bash
$ npx http-server doc/website/build/coursier
```
to browse the website. This command starts a website to browse the generated
documentation (its address should be printed in the console).

## Watch mode

To run the website while watching its sources (which live under `doc/docs`), run once
```bash
$ ./mill -i doc.generate --npm-install --yarn-run-build
```

Then run the following command to actually watch the documentation markdown files for changes:
```bash
$ ./mill -i doc.generate --npm-install --yarn-run-build --watch
```

This runs both docusaurus and mdoc in watch mode. The former should open
a browser window, that automatically refreshes upon changes.

## Serving the website

To build the website and get a command serving it, run
```bash
$ eval "$(./mill show doc.serveCmd | jq -r .)"
```

To run the Docusaurus development server in the background instead, run
```bash
$ ./mill -w doc.devServer
```

It reloads pages as their sources change. Stop it with `./mill doc.devServerStop`.

The search index is computed when building the website, so search only works on the
built website (`doc.serveCmd`), and not with the development server.

Both accept `--host=…`, `--port=…` (3000 by default), and `--base-address=…`, the
external address the website is accessed from (like `https://host/some/path/`),
when it's served behind a proxy. The `website` and `website-static` entries of
`services.json` use them.
