# AGENTS Guide for `coursier/coursier`

This guide is for autonomous coding agents working in this repository.
Prefer minimal, local, reversible changes and follow existing patterns.

## Project snapshot

- Build tool: Mill (`./mill`) with config in `build.mill`.
- Languages: Scala (JVM + Scala.js), some Java, docs tooling.
- Test framework: uTest (`utest.runner.Framework`).
- Active cross Scala versions: `2.13.16`, `2.12.20`.
- Formatting: Scalafmt (`.scalafmt.conf`, max column 100).
- CI source of truth: `.github/workflows/ci.yml`.

## Initial setup

- Initialize submodules before deeper work:
  - `git submodule update --init --recursive`
- For Scala.js / website work, install JS deps first:
  - `npm install --ignore-scripts`
- CI uses JDK 21; matching that reduces surprises.

## Build commands

- Compile all modules:
  - `./mill __.compile`
- Compile Scala.js modules (CI pattern):
  - `./mill -j1 __.js[_].compile`
- Publish all modules locally (heavy):
  - `./mill -i __.publishLocal`
- Build docs markdown and mkdocs site:
  - `./mill -i docs.mdoc`
  - `./mill -i docs.mkdocsBuild`
- Build legacy docs website output:
  - `./mill -i doc.generate --npm-install --yarn-run-build`

## Lint and formatting

- Check formatting (matches CI):
  - `scalafmt --check`
- Apply formatting:
  - `scalafmt`
- No default repo-wide Scalafix task is wired in CI.
- Treat compiler warnings as actionable.

## Test commands

- Run all JVM aggregate tests:
  - `./mill -i jvmTests`
- Run JVM aggregate tests for one Scala version:
  - `./mill -i jvmTests --scalaVersion 2.13.16`
- Run all Scala.js aggregate tests:
  - `./mill -i jsTests`
- Run Scala.js aggregate tests for one Scala version:
  - `./mill -i jsTests --scalaVersion 2.13.16`
- Run docker-focused tests:
  - `./mill -i dockerTests`
- Run native-launcher tests:
  - `./mill -i nativeTests`

## Running a single test (important)

Cross module names include brackets, so quote task paths in shells.

- Run one test module:
  - `./mill 'tests.jvm[2.13.16].test.testForked'`
- Run one test class via `testOnly`:
  - `./mill 'tests.jvm[2.13.16].test.testOnly' coursier.tests.VersionTests`
- Run one class in another module:
  - `./mill 'core.jvm[2.13.16].test.testOnly' coursier.core.MavenVersioningTests`
- List discovered tests if unsure about names:
  - `./mill 'tests.jvm[2.13.16].test.discoveredTestClasses'`

Notes:
- Most suites are `object ... extends TestSuite` with names ending in `Tests`.
- For JS tests, use task paths like `*.js[2.13.16].test.*`.

## High-value verification patterns

- Scala/JVM code changes:
  - `./mill __.compile`
  - `./mill -i jvmTests --scalaVersion 2.13.16`
- Scala.js code changes:
  - `npm install --ignore-scripts`
  - `./mill -i -j1 '__.js[2.13.16].compile'`
  - `./mill -i jsTests --scalaVersion 2.13.16`
- Docs changes:
  - `./mill -i docs.mdoc`
  - `./mill -i docs.mkdocsBuild`
- Public API / compatibility-sensitive changes:
  - `./mill -i -k __.mimaReportBinaryIssues`

## Code style guidelines

### Formatting

- Let Scalafmt drive layout; do not manually fight formatter output.
- Keep changes focused; avoid unrelated formatting churn.
- Keep lines and wrapping compatible with existing style.

### Imports

- Keep imports grouped with blank lines between groups:
  1) Java/Scala stdlib
  2) third-party dependencies
  3) internal `coursier.*` imports
- Prefer explicit imports in production code when practical.
- Wildcard imports are acceptable where already idiomatic (notably `utest._`).
- Remove unused imports whenever touching a file.

### Types and APIs

- Add explicit result types for public or widely reused methods.
- Use inference for obvious local vals/helpers.
- Prefer existing domain types over primitive strings when available.
- Consider binary compatibility for public APIs (MiMa is part of CI).

### Naming

- Package names: lowercase.
- Types/objects/traits: PascalCase.
- Methods/vals/vars: camelCase.
- Tests: descriptive names, usually ending in `Tests`.

### Error handling

- Fail explicitly with useful context rather than silently ignoring issues.
- Use `NonFatal` for broad exception catches in runtime code.
- In build logic, use task failures / `sys.error` for unrecoverable states.
- Avoid swallowing exceptions unless behavior is intentionally best-effort.

### Concurrency and resources

- Reuse existing execution-context patterns in each module.
- Ensure temp files/dirs are cleaned in tests.
- Prefer structured resource handling (`Using`, `try/finally`) where needed.
- Preserve platform guards (`Properties.isWin`, etc.) in cross-platform paths.

### Testing approach

- Add tests close to the changed module (`modules/*/(jvm|js|shared)/src/test`).
- Prefer targeted unit tests before broad suites.
- Keep tests deterministic and CI-friendly.
- Reuse existing test infra helpers for network/repository scenarios.

## Build definition conventions

- Follow existing `Cross` + shared-source composition patterns.
- Keep Scala-version logic centralized (typically `mill-build/src/.../Deps.scala`).
- Avoid hardcoding duplicated version values across files.
- Keep CI and local command docs aligned when adding top-level tasks.

## Agent workflow

1. Read nearby code and mirror local idioms.
2. Implement the smallest change that solves the task.
3. Run narrow tests first, then broader checks.
4. Report exact commands run and any skipped checks.

## Cursor / Copilot rules in this repo

- No `.cursor/rules/` directory found.
- No `.cursorrules` file found.
- No `.github/copilot-instructions.md` file found.
- If any appear later, treat them as authoritative and update this file.
