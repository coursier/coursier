# Setup channel snapshot

These JSON files are a snapshot of the app descriptors from the default
channel (https://github.com/coursier/apps, `apps/resources` directory),
limited to the apps installed by `cs setup` (see
`coursier.cli.setup.DefaultAppList`).

They let `SetupTests` run `cs setup` against a local channel directory, so that
the test does not depend on the `coursier/apps` repository / channel being
reachable. `SetupTests` runs the `setup` test both against this snapshot and
against the live default channel.

To refresh the snapshot, copy the relevant `*.json` files from a clone of the
coursier/apps repository here:

```
git clone https://github.com/coursier/apps.git /tmp/apps
cp /tmp/apps/apps/resources/{cs,coursier,scala,scalac,scala-cli,sbt,sbtn,scalafmt}.json .
```
