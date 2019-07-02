---
title: Version handling
---

This page aims at describing how versions are reconciled in coursier,
in particular when version intervals are involved.

## Reconciliation

Version reconciliation happens when two or more of your direct or
transitive dependencies depend on different versions of the same module.
Version reconciliation then either chooses one version for that module, or
reports a conflict if it can't reconcile those versions.

### Algorithm

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

### Examples

#### Non-overlapping intervals

If you depend on both `[1.0,2.0)` and `[3.0,4.0)`, there's a conflict.

If you depend on `[1.0,2.0)`, `[1.5,2.5)`, and `[2.0,3.0)`, there's a conflict
(no intersection shared by all three intervals).

#### Specific versions below interval are ignored

If you depend on `[1.0,2.0)` and `0.9`, `0.9` is ignored. It is assumed
a version in the `[1.0,2.0)` range will be fine where `0.9` is needed.

If you depend no `[1.0,3.0)`, `[2.0,4.0)`, and `1.3`, the intersection
of the intervals is `[2.0,3.0)`, and `1.3` is ignored.

#### Specific versions above the intervals result in a conflict

If you depend on `[1.0,2.0)` and `2.2`, there's a conflict. Versions in
`[1.0,2.0)` are below `2.2`, and it is assumed a version below `2.2` cannot
be fine where `2.2` is needed.

If you depend on `[1.0,2.0)`, `[1.4,3.0)`, and `2.1`, there's a conflict.
The intersection of the intervals is `[1.4,2.0)`, and `2.1` is above it,
which results in a conflict.

#### Specific versions in interval are preferred

If you depend on `[1.0,2.0)` and `1.4`, version reconciliation results in `1.4`.
As there's a dependency on `1.4`, it is preferred over other versions in `[1.0,2.0)`.

If you depend on `[1.0,2.0)`, `1.4`, and `1.6`, version reconciliation chooses `1.6`.
It is assumed `1.6` is fine where `1.4` is needed, and `1.6` is in `[1.0,2.0)`.

## Ordering

Version ordering in coursier was adapted from Maven.

To be compared, versions are splitted into "items". These items are then
compared using lexicographic order. Zero numeric items are appended to the
shortest version so that both list of items have the same length.

To get items, versions are split at `.`, `-`, and `_` (and those separators
are discarded), and at letter-to-digit or digit-to-letter switches.

Numeric items are compared with their numeric values. Literal items are compared
lexicographically, ignoring the case. Literal items go before non-zero numeric items, and after numeric items made of zeros.

For example:
- `1.0.1` has the same ordering as `1.0-1` (actual separators don't matter)
- `1.0.1` or `1.0.1.0` goes before `1.0.1e` (literal `e` goes after empty / zero items)
- `1.0.1e` goes before `1.0.1.2` (literal `e` goes before non-zero numeric item `2`)
- `1.1.0` has same ordering as `1.1` (zero or empty items are equivalent)

Some literal items have a special meaning, and go before both literal and
zero and non-zero numeric items. These are, in comparison order:
- `alpha` (or `a` if directly followed by a digit),
- `beta` (or `b` if directly followed by a digit),
- `milestone` (or `m` if directly followed by a digit),
- `cr` or `rc`,
- `snapshot`,
- `ga` or `final`,
- `sp`.

Note that the case doesn't matter, so `RC` is equivalent to `rc` for example.

That gives the following comparisons:
- `1.1-alpha` goes before `1.1-rc` (qualifier `alpha` before `rc`)
- `1.1-rc` goes before `1.1-final` (qualifier `rc` before `final`)
- `1.1-final` goes before `1.1` (qualifier `final` before empty item)
- `1.1` goes before `1.1a` (empty item before literal `a`, see below)
- `1.1a` goes before `1.1-foo` (literal item `a` before literal `foo`, see below)

Note that `1.1a` is not equivalent to `1.1-alpha`, as `a` is not followed
by a digit. On the other hand, `1.1a1` is equivalent to `1.1-alpha-1`,
and `1.1a-1` is not, as `a` is followed by `-`, not by a digit.

A last rule consists in ignoring any `0` items before a literal.

For example:
- both `1.0-alpha-1` and `1.0.0-alpha-1` go before `1-beta`, `1.0-beta`, and `1.0.0-beta`
