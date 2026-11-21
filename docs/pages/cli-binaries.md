# Binaries

CLI is available as native binaries, and as
a JAR that needs an already installed JVM to run.

Use binaries if one is available for your system (Linux and Mac on x86-64 and ARM64, Windows on x86-64)

Else install a JVM on your own and use the JVM launcher (Windows on ARM64, *BSD, …)

Linux binaries are available for both x86-64 and ARM64. Extra shades of Linux binaries are
also available for both CPU architectures: static binaries (no dynamic library dependencies, suitable
for systems like Alpine), container binaries (`-H:-UseContainerSupport`, https://github.com/coursier/coursier/pull/2742)

The standard, container and compat Linux binaries need glibc 2.34 or later (since coursier 2.1.25),
and zlib (`libz.so.1`). So they run on Ubuntu 22.04 LTS or later, Debian 12 or later,
RHEL / Rocky Linux / AlmaLinux 9 or later, Fedora 35 or later, Amazon Linux 2023,
and openSUSE Leap 15.6 / SLES 15 SP6 or later. On older distributions, use the static binaries
or the JVM launcher.
