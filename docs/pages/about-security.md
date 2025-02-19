# Security

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
