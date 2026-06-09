# Setup channel snapshot

These JSON files are a snapshot of the app descriptors from the
`io.get-coursier:apps` channel (built from https://github.com/coursier/apps),
limited to the apps installed by `cs setup` (see
`coursier.cli.setup.DefaultAppList`).

They let `SetupTests` run `cs setup` against a local channel directory, so that
the test does not depend on the `coursier/apps` repository / channel being
reachable. `SetupTests` runs the `setup` test both against this snapshot and
against the live default channel.

To refresh the snapshot, grab the latest `io.get-coursier:apps` jar and copy
the relevant `*.json` entries here:

```
cs fetch io.get-coursier:apps:latest.release
unzip -o <path-to-apps.jar> -d /tmp/apps
cp /tmp/apps/{cs,coursier,scala,scalac,scala-cli,sbt,sbtn,scalafmt}.json .
```
