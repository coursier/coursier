# Coursier Archive Cache

The archive cache offers to unpack artifacts downloaded by the [main coursier cache](features-cache.md).
It unpacks them in a predefined and stable location, so that things need to be unpacked only once,
and can be re-used many times after that.

For example, [this archive](https://cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip) ends up being unpacked
in this location on Linux:
```text
~/.cache/coursier/arc/https/cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip/
```

Note the `arc` component in the path, where paths of the [main coursier cache](features-cache.md) contain
a `v1` instead.

One can get such a path from the command-line, with:
```text
$ cs get --archive https://cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip
~/.cache/coursier/arc/https/cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip

$ ls "$(cs get --archive https://cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip)"
LICENSE.gpl2
LICENSE.gpl3
LICENSE.lgpl2
LICENSE.lgpl3
README.md
aarch64-linux-cosmo
bin
include
lib
libexec
x86_64-linux-cosmo
```

This command downloads the artifact via the main coursier cache if needed, then
unpacks it in the archive cache if needed. If the archive is already unpacked in the
main archive cache, it doesn't check or try to download the artifact in the main
coursier cache in the first place.

## Standalone use

While various features of coursier rely on the archive cache, it can be used in a standalone fashion
[via its API](api-archive-cache.md) or [via the CLI](cli-archive-cache.md).

## Cache location

The exact location of the archive cache is OS-dependent. Like for
[the main coursier cache](features-cache.md#cache-location), it relies on the
[directories-jvm](https://github.com/dirs-dev/directories-jvm) library (more precisely,
[a slightly customized fork of it](https://github.com/coursier/directories-jvm)) to follow
OS conventions and put the coursier cache at the most appropriate location for your OS.

| OS | Location | Note |
|----|----------|------|
| Linux | `~/.cache/coursier/arc/` | XDG… |
| macOS | `~/Library/Caches/Coursier/arc/` |      |
| Windows | `C:\Users\username\AppData\Local\Coursier\Cache\arc` | Windows API… |

## Archive content addressing

One can get particular files in archives, by separating the archive URL and the path
inside the archive with `!`, like
```text
$ cs get --archive 'https://ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb!data.tar.xz'
~/.cache/coursier/arc/https/ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb/data.tar.xz
```

Note that if the URL passed to `cs get` contains `!`, it automatically assumes it deals with archives,
so that `--archive` doesn't have to be passed:
```text
$ cs get 'https://ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb!'
~/.cache/coursier/arc/https/ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb
```

## Archives in archives

Using `!` like [above](#archive-content-addressing) multiple times, one can get files from
archives contained themselves in archives:
```text
$ cs get --archive 'https://ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb!data.tar.xz!usr/bin/hello'
~/.cache/coursier/arc/https/ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb/.data.tar.xz/usr/bin/hello

$ cs get 'https://ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb!data.tar.xz!usr/share/doc/hello/changelog.gz!'
~/.cache/coursier/arc/https/ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb/data.tar.xz/usr/share/doc/hello/.changelog.gz/changelog
```
