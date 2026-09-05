# Unroll Fork

`modules/unroll/plugin` contains a locally vendored fork of the upstream `com-lihaoyi/unroll`
compiler plugin.

Upstream project:
- https://github.com/com-lihaoyi/unroll

Licensing:
- The code under `modules/unroll/plugin` retains the upstream MIT license.
- See `modules/unroll/LICENSE` for the license text.
- Modifications to vendored code will retain the MIT license so they can be upstreamed as necessary.

Local use in this repository:
- This fork is built by `build.mill` (`unroll.plugin`) as the local `unroll-plugin` compiler plugin,
  for Scala 3 only. It replaces the [data-class](https://github.com/alexarchambault/data-class)
  macros there, which keep being used on Scala 2.
- `modules/unroll/dataclass` is a small Scala 3 stand-in for the data-class annotations (`@data` is a
  no-op, `@since` is an alias for `@com.lihaoyi.unroll`), so that sources shared between
  Scala 2 and Scala 3 don't need to change.
- The published `unroll-annotation` dependency is still consumed from Maven.

Provenance:
- This subtree was imported from the vendored copy in
  [chipsalliance/chisel](https://github.com/chipsalliance/chisel), itself imported from upstream
  commit c9fd7e1ce9a92fe4ab15cd12fb61facc3aed8f2c (tag 0.3.0).
- Local modifications (Scala 3 plugin, `UnrollPhaseScala3.scala`):
  - only the compiler-generated `apply` / `fromProduct` / `copy` of case classes (flagged
    `Synthetic`) are special-cased, so that hand-written `apply` overloads in case class
    companions no longer make the plugin fail with "wrong number of arguments at unroll2";
  - a forwarder is not generated when an overload with the same signature is defined by hand
    (the shorter forwarders call that overload instead), so that hand-written overloads take
    precedence rather than clashing with generated ones.
- Local addition (`DataClassCopyPhaseScala3.scala`, registered by the same plugin): the `copy` of a
  `@data` case class with type parameters keeps the type parameters of the class rather than taking
  fresh ones, and the parameters of a trailing implicit clause get the corresponding field as
  default value, as the data-class macro generates them on Scala 2. Without this, `F` is
  unconstrained in the `def copy[F[_]](…)(implicit sync: Sync[F]): FileCache[F]` Scala 3 generates,
  and `fileCache.copy(location = dir)` fails to find a `Sync[F]`. Since this changes a signature
  callers see, that phase runs before the pickler, unlike the other two.
- Local addition (`DataClassPhaseScala3.scala`, registered by the same plugin): implements two
  arguments of the data-class `@data` annotation:
  - `cachedHashCode = true`: the synthetic `hashCode` of the annotated case class is cached in a
    private field, as the Scala 2 macro does;
  - `settersCallApply = true`: the synthetic `copy` (and its unroll forwarders) and `fromProduct`
    create instances via the hand-written companion `apply` with the constructor's signature, as
    the Scala 2 macro's setters and `copy` do (data-class >= 0.2.9 for `copy`).
