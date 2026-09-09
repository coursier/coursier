---
title: JSON report
---

When invoking coursier cli with
```
fetch -t <modules...> --json-output-file <report.json>
```

The report will contain the info about resolved modules and their relationships.

Pass `--json-report-add-urls` alongside `--json-output-file` to also record, for each
artifact, the URL it was fetched from.

## Format and Version Change Log

### 0.1.0

Report generation was rewritten. Per dependency, the `files` array is replaced by a single
`file` field, and dependencies with several artifacts (say, a JAR and its tests JAR) now get
one entry each, the classifier being carried by `coord` rather than by the file entry.
`directDependencies` and `exclusions` fields are added.

```
{
  "conflict_resolution": {
    "org:name:version" (requested): "org:name:version" (reconciled)
  },
  "dependencies": [
    {
      "coord": "orgA:nameA:versionA",
      "file": <path>,
      "url": <url>,                  // only with --json-report-add-urls, see below
      "directDependencies": [        // coordinates of its direct dependencies
        <orgX:nameX:versionX>
      ],
      "dependencies": [              // coordinates of its transitive dependencies
        <orgX:nameX:versionX>,
        <orgY:nameY:versionY>
      ],
      "exclusions": [                // "org:name" pairs excluded by this dependency
        <orgZ:nameZ>
      ]
    }
  ],
  "version": "0.1.0"
}
```

`url` is only written when `--json-report-add-urls` is passed, and is left out of the entry
otherwise - it was added without a version bump, so a `0.1.0` report may or may not have it.
It's the URL the file was actually fetched from: if a mirror is configured, that's the
mirror URL, not the one the repository the dependency was found in would have used. Note
that `coord` doesn't necessarily follow from it, as repositories can serve an artifact from
an arbitrary URL.

### 0.0.1

Add 'version' field to the report.

```
{
  "version": "0.0.1",
  "conflict_resolution": {
    ...
  },
  "dependencies": [
    ...
  ]
}
```

### Initial version (before we add the version to the report)

```
{
  "conflict_resolution": {
    "org:name:version" (requested): "org:name:version" (reconciled)
  },
  "dependencies": [
    {
      "coord": "orgA:nameA:versionA",
      "files": [
        [
          <classifier>,
          <path>
        ]
      ],
      "dependencies": [ // coordinates for its transitive dependencies
        <orgX:nameX:versionX>,
        <orgY:nameY:versionY>,
      ]
    },
    {
      "coord": "orgB:nameB:versionB",
      "files": [
        [
          <classifier>,
          <path>
        ]
      ],
      "dependencies": [ // coordinates for its transitive dependencies
        <orgX:nameX:versionX>,
        <orgZ:nameZ:versionZ>,
      ]
    },
  ]
}
```
For example:
```
fetch -t org.apache.avro:trevni-avro:1.8.2  org.slf4j:slf4j-api:1.7.6 --json-output-file x.out
  Result:
├─ org.apache.avro:trevni-avro:1.8.2
│  ├─ org.apache.avro:trevni-core:1.8.2
│  │  ├─ org.apache.commons:commons-compress:1.8.1
│  │  ├─ org.slf4j:slf4j-api:1.7.7
│  │  └─ org.xerial.snappy:snappy-java:1.1.1.3
│  └─ org.slf4j:slf4j-api:1.7.7
└─ org.slf4j:slf4j-api:1.7.6 -> 1.7.7
```
would produce the following json file:
```
$ jq < x.out
{
  "conflict_resolution": {
    "org.slf4j:slf4j-api:1.7.6": "org.slf4j:slf4j-api:1.7.7"
  },
  "dependencies": [
    {
      "coord": "org.apache.avro:trevni-core:1.8.2",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/apache/avro/trevni-core/1.8.2/trevni-core-1.8.2.jar"
        ]
      ],
      "dependencies": [
        "org.slf4j:slf4j-api:1.7.7",
        "org.xerial.snappy:snappy-java:1.1.1.3",
        "org.apache.commons:commons-compress:1.8.1"
      ]
    },
    {
      "coord": "org.apache.avro:trevni-avro:1.8.2",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/apache/avro/trevni-avro/1.8.2/trevni-avro-1.8.2.jar"
        ]
      ],
      "dependencies": [
        "org.apache.avro:trevni-core:1.8.2",
        "org.slf4j:slf4j-api:1.7.7",
        "org.xerial.snappy:snappy-java:1.1.1.3",
        "org.apache.commons:commons-compress:1.8.1"
      ]
    },
    {
      "coord": "org.slf4j:slf4j-api:1.7.7",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.7/slf4j-api-1.7.7.jar"
        ]
      ],
      "dependencies": []
    },
    {
      "coord": "org.apache.commons:commons-compress:1.8.1",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar"
        ]
      ],
      "dependencies": []
    },
    {
      "coord": "org.xerial.snappy:snappy-java:1.1.1.3",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.1.3/snappy-java-1.1.1.3.jar"
        ]
      ],
      "dependencies": []
    }
  ]
}
```