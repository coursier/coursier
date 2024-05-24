Chunks from Maven / Ivy repositories, used by coursier during its tests.

To update those, run JVM tests in coursier with `FILL_CHUNKS=1` in the
environment. Then go to its `tests/metadata` directory (which is a git
submodule), (git) add the newly-created files, commit that, push the result
somewhere, open a PR here.
