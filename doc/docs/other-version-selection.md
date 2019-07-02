---
title: Version reconciliation
---

This page aims at describing how versions are reconciled in coursier,
in particular when version intervals are involved.


Version reconciliation happens when two or more of your direct or
transitive dependencies depend on different versions of the same module.
Version reconciliation then either chooses one version for that module, or
reports a conflict if it can't reconcile those versions.

## Algorithm

Version reconciliation is handed several:
- specific versions, like `1.2.3` or `2.0.0-M2`, and / or
- version intervals, like `[1.0,2.0)` or `2.0+`.

Here, we're going to ignore:
- `latest.*` versions like `latest.release` or `latest.integration`, that are
handled via different mechansims, and
- unions of intervals, like `[1.0,1.2),[1.3,1.4)`, that are currently
unsupported in coursier (only the last interval is retained, all the others
are discarded).

From several specific versions and / or version intervals, version reconciliation
results in either:
- a conflict if the input versions can't be reconciled, or
- a single resulting specific version, or
- a single resulting version interval.

That output is calculated the following way:
- Take the intersection of the input intervals. If it's empty (the intervals
don't overlap), there's a conflict. If there are no input intervals, assume
the intersection is `(,)` (interval matching all versions).
- Then look at specific versions:
  - Ignore the specific versions below the interval.
  - If there are specific versions above the interval, there's a conflict.
  - If there are specific versions in the interval, take the highest as result.
  - If there are no specific versions in or above the interval, take the
    interval as result.

Note that if the result is in interval, and no other reconciliation happens
down the line, the highest version in the interval ends up being selected.

On the other hand, if the result is a specific version, that version ends up
being selected. In a way, a specific version like `1.2` is loosely equivalent
to an interval `[1.2,)`, except that depending on `1.2` will result in `1.2`
being selected, whereas depending on `[1.2,)` will result in the highest
available version >= `1.2` being selected.

To clarify, the examples below illustrate what happens in a number of cases.

## Examples

### Non-overlapping intervals

If you depend on both `[1.0,2.0)` and `[3.0,4.0)`, there's a conflict.

If you depend on `[1.0,2.0)`, `[1.5,2.5)`, and `[2.0,3.0)`, there's a conflict
(no intersection shared by all three intervals).

### Specific versions below interval are ignored

If you depend on `[1.0,2.0)` and `0.9`, `0.9` is ignored. It is assumed
a version in the `[1.0,2.0)` range will be fine where `0.9` is needed.

If you depend no `[1.0,3.0)`, `[2.0,4.0)`, and `1.3`, the intersection
of the intervals is `[2.0,3.0)`, and `1.3` is ignored.

### Specific versions above the intervals result in a conflict

If you depend on `[1.0,2.0)` and `2.2`, there's a conflict. Versions in
`[1.0,2.0)` are below `2.2`, and it is assumed a version below `2.2` cannot
be fine where `2.2` is needed.

If you depend on `[1.0,2.0)`, `[1.4,3.0)`, and `2.1`, there's a conflict.
The intersection of the intervals is `[1.4,2.0)`, and `2.1` is above it,
which results in a conflict.

### Specific versions in interval are preferred

If you depend on `[1.0,2.0)` and `1.4`, version reconciliation results in `1.4`.
As there's a dependency on `1.4`, it is preferred over other versions in `[1.0,2.0)`.

If you depend on `[1.0,2.0)`, `1.4`, and `1.6`, version reconciliation chooses `1.6`.
It is assumed `1.6` is fine where `1.4` is needed, and `1.6` is in `[1.0,2.0)`.
