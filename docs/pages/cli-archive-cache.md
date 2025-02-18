# Archive cache

From the CLI, one can interact directly with the archive cache with `cs get --archive`. One can also
adjust some archive cache parameters, that should apply to all applications using the coursier archive
cache.

Pass `--archive` to `cs get` to download and extract an archive:
```text
$ cs get --archive https://cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip
~/.cache/coursier/arc/https/cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip
```

## Environment variables

### Cache location

Adjust the cache location with `COURSIER_ARCHIVE_CACHE`:
```text
$ export COURSIER_ARCHIVE_CACHE="/some/other/archive/location"
```
