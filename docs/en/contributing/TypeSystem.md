# Type System (v2) — Architecture & Tuning Notes

This document describes the type inference subsystem introduced under
`com.github._1c_syntax.bsl.languageserver.types` and how to tune it for
large workspaces.

## Components

| Layer | Class | Role |
|-------|-------|------|
| Model | `types.model.Type`, `TypeRef`, `TypeSet`, `MemberDescriptor` | Sealed type model. `TypeRef` is interned and used as a memory-cheap key everywhere instead of a fully hydrated `Type`. |
| Registry | `types.registry.TypeRegistry` | Global, eager. Aggregates type definitions from `PlatformTypesProvider` (built-in JSON), `ConfigurationTypesProvider` (MDClasses), `ConfigurationModuleMembersProvider` (extends platform `СправочникМенеджер.X` etc. with user methods from object/manager modules), and `UserTypesProvider` (OneScript classes). Composition: multiple `MemberSource`s can extend the same `TypeRef`. |
| Symbol typing | `types.index.SymbolTypeIndex` | Eager declarative types: parameter/return/variable types from `bsl-parser` `MethodDescription` / `VariableDescription`. Filled in the same pass as `ReferenceIndexFiller`. |
| Inference | `types.inferencer.ExpressionTypeInferencer` | Lazy, on-demand inference over `ExpressionTree`. Uses `InferenceContext` for cycle protection. Caffeine cache per `(WeakKey ExpressionTreeNode)`. |
| Facade | `types.TypeService` | Single entry point for consumers (`HoverProvider`, `CompletionProvider`, future `SignatureHelpProvider`). |

## Memory characteristics

For ~10 M LOC codebases the dominant costs come from:

1. **Type identity** — addressed by interning `TypeRef` instances inside
   `TypeRegistry`. Symbols and the index hold only the lightweight `TypeRef`
   key, never a hydrated `Type`.
2. **Symbol → declared types** — `SymbolTypeIndex` keeps a single
   `ConcurrentHashMap<symbolKey, TypeSet>`; `TypeSet` is immutable and
   structurally shared (the same set of refs hashes to the same instance via
   `TypeSet.of(...)`).
3. **Inferred expression types** — kept in a Caffeine cache with weak keys.
   The keys are `ExpressionTreeNode` instances; on document re-parse the AST
   is rebuilt and the old nodes become eligible for GC, so the cache
   self-cleans.

## Tuning knobs

The Caffeine caches are configured at construction in
`ExpressionTypeInferencer`. Defaults are conservative; for very large
workspaces the following knobs may need tuning (currently constants in the
class — make them `@Value`-driven if needed):

* `maximumSize` of the per-expression cache. Default is `100_000`.
  Increase if hot paths show repeated re-inference of the same nodes;
  decrease if heap pressure is high.
* `INFERENCE_DEPTH_LIMIT` in `InferenceContext`. Default `32`. Cycles in
  declared types (`a = b; b = a;`) are intercepted by the visited-symbols
  set, so this is a safety net; rarely needs changing.

## Benchmarking

Use the `TypeServiceBenchmarkTest` (under `src/test/.../types/`). It:

* loads the existing fixture under `src/test/resources/types/`,
* warms up the inferencer on every variable in the symbol tree,
* repeats `findTypes(...)` N times and reports wall-clock per call.

Run with:

```
./gradlew test --tests "*TypeServiceBenchmarkTest" --info
```

The output is informational; the test always passes. For real performance
work, capture before/after numbers on the same machine.

## Extension points

* **`PlatformTypesProvider`** — additional implementations can come from an
  external `platform-context` (full 1C platform members) or from a JSON of
  the OneScript syntax helper. Multiple providers may coexist.
* **`UserTypesProvider`** — currently registers OneScript class modules.
  Common modules are intentionally kept as simple namespaces.
* **Functional types (`sfaqer/lambdas`)** — not implemented in v2; the model
  intentionally leaves a `TypeKind` slot for a future provider that would
  parse string-literal lambdas and produce a callable type.
