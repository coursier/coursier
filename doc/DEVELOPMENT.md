# Cookbook of stuff to do while developing on coursier

The sources of coursier rely on some git submodules. Clone the sources of coursier via
```
$ git clone --recursive https://github.com/coursier/coursier.git
```
or run
```
$ git submodule update --init --recursive
```
from the coursier sources to initialize them.

The latter command also needs to be run whenever these submodules are updated.

# Merging PRs on GitHub

Use either "Create merge commit" or "Squash and merge".

Use "Create merge commit" if the commit list is clean enough (each commit has a clear message, and doesn't break simple compilation and test tasks).

Use "Squash and merge" in the other cases.

# General Versioning Guideline

* Major Version 1.x.x : Increment this field when there is a major change.
* Minor Version x.1.x : Increment this field when there is a minor change that breaks backward compatibility for an method.
* Patch version x.x.1 : Increment this field when a minor format change that just adds information that an application can safely ignore.

# Deprecation Strategy

When deprecating a method/field, we want to know
1. Since which version this field/method is being deprecated
2. Migration path, i.e. what to use instead
3. At which point the deprecation will be removed

Due to scala's builtin deprecation works like
```
class deprecated(message: String = {}, since: String = {})
```
we need to put 2) and 3) into `message`:
```
@deprecated(message = "<migration path>. <version to be removed>", since: "deprecation start version")
```

Typically there needs to be at least 2 minor versions between since-version and to-be-removed-version to help migration.

For example, if since version is 1.1.0, then deprecation can be removed in 1.3.0
