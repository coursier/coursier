# Security

## Dependency verification

### Checksum verification

coursier verifies SHA-1 checksums for downloaded artifacts when the corresponding `.sha1`
files are available on the repository. If no checksum file is available, coursier accepts
the artifact without checksum verification.

Which checksum types are checked, and whether missing checksums are accepted, can be
customized via coursier's API using `FileCache#checksums` and `FileCache#withChecksums`.

As of now, checksum behavior can only be changed through coursier's API, not via
environment variables or Java system properties.

### Signature verification

coursier does not perform PGP signature verification. It is possible to make coursier
fetch `.asc` signature files via the API, but no verification of those signatures is
performed by coursier itself.

### Verification metadata

coursier does not support a verification metadata approach comparable to Gradle's
`verification-metadata.xml`. Support for this could be added in the future.

### SCA tool integration

coursier does not currently generate a Software Bill of Materials (SBOM) during
dependency resolution.
[Mill](https://mill-build.org), which uses coursier for dependency resolution,
has [early SBOM support](https://github.com/com-lihaoyi/mill/pull/4757).

## CVE

As of writing this, coursier has been indirectly impacted by two CVEs:
- [CVE-2022-46751](https://www.cve.org/CVERecord?id=CVE-2022-46751)
- [CVE-2022-37866](https://www.cve.org/CVERecord?id=CVE-2022-37866)

See [the contact page](about-contact.md) if you need to reach coursier maintainers
for urgent non-public security-related issues.

## Artifact attestations

coursier use the [actions/attest-build-provenance](https://github.com/actions/attest-build-provenance)
GitHub action to attest that the binaries it distributes on its release pages were indeed built
by the GitHub Action runners themselves. Attestations can be found
[here](https://github.com/coursier/coursier/attestations).
