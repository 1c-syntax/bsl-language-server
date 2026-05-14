# Type System (v2) — Architecture & Tuning Notes

This document describes the type inference subsystem under
`com.github._1c_syntax.bsl.languageserver.types` and how to tune it for
large workspaces. The subsystem is **workspace-scoped**: every Spring
`workspace` scope (an LSP workspace folder) owns its own `TypeRegistry`
and `GlobalScopeProvider`, so multi-root setups are isolated.

## Terminology

1C / OneScript have no notion of *namespace* — only **properties** and
**methods**. Names like `Справочники`, `КодировкаТекста`, `ФС`,
`ОбщегоНазначения` are therefore modelled as **global properties**:
synthetic symbols of `SyntheticKind.PLATFORM_GLOBAL_PROPERTY` (or
`CONFIGURATION_OBJECT`) whose **value type** carries the members reachable
through dot-access. OneScript library modules (a variable name from
`lib.config` `<module>` entries or a conventional `oscript_modules/<lib>/Модули/<Name>.os`
file) are exposed the same way, but tagged as `LIBRARY_MODULE`.

## Components

| Layer | Class | Role |
|-------|-------|------|
| Model | `types.model.Type`, `TypeRef`, `TypeSet`, `MemberDescriptor` | Sealed type model. `TypeRef` is interned and used as a memory-cheap key everywhere instead of a fully hydrated `Type`. `MemberDescriptor` can carry a `sourceSymbol` weak ref back to its declaring `SourceDefinedSymbol`. |
| Registry | `types.registry.TypeRegistry` | Workspace-scoped, lazy. Aggregates type definitions from `BuiltinPlatformTypesProvider` (built-in JSON), `ConfigurationTypesProvider` (MDClasses), `ConfigurationModuleMembersProvider` (extends platform `СправочникМенеджер.X` etc. with user methods), and `UserTypesProvider` (OneScript classes). Composition: multiple `MemberSource`s can extend the same `TypeRef`. |
| Global scope | `types.registry.GlobalScopeProvider` + `types.scope.GlobalSymbolScope` | Single facade over the workspace's global names: platform classes (`Новый X`), global functions, OneScript library modules / classes, and **global properties**. Backed by `SyntheticSymbol`s, so consumers can use one symbol-shaped API instead of N parallel indexes. |
| OneScript libs | `types.oscript.*` | `LibConfigDiscovery` / `ConventionalLibraryDiscovery` find roots under `oscript_modules/`; `LibConfigParser` / `OScriptLibraryFileParser` build the index; `OScriptLibraryIndex` registers types via `TypeRegistry` and `GlobalScopeProvider`; `OScriptModuleTypeResolver` fixes `ModuleType.OScriptClass / OScriptModule`. |
| Symbol typing | `types.index.SymbolTypeIndex` | Declarative types: parameter / return / variable types from `bsl-parser` `MethodDescription` / `VariableDescription`. Filled in the same pass as `ReferenceIndexFiller`. |
| Inference | `types.inferencer.ExpressionTypeInferencer` | Lazy, on-demand inference over `ExpressionTree`. Uses `InferenceContext` for cycle protection. Caffeine cache per `(WeakKey ExpressionTreeNode)`. |
| Facade | `types.TypeService` | Single entry point for consumers: `findTypes(uri, pos)`, `findTypes(Reference|SourceDefinedSymbol)`, `inferAtPosition`, `findMemberAt`, `getParameterTypes`, `getMembers`, `findGlobalPropertyType`, `getGlobalPropertyNames`. |

## Public API

* **Registry**
  * `registerAsGlobalProperty(TypeRef)` — expose a registered type as a
    global property (canonical name + aliases come from the type itself).
  * `registerMemberSource(TypeRef, Supplier<List<MemberDescriptor>>)` —
    add a member contributor (multiple sources are merged).
  * `resolve(name)` / `getMembers(ref)` / language alias helpers.
* **Global scope**
  * `registerGlobalProperty(TypeRef, names)`,
    `findGlobalPropertyType(name)`, `getGlobalPropertyNames()`.
  * `registerLibraryModule` / `registerLibraryClass`
    (+ `unregisterLibraryModule/Class` for re-index on edit).
  * `findGlobal(name)` returns the `SyntheticSymbol` (consumer can ask
    its kind to decide UI affordances: class, module, global property,
    global function).
* **Type service** wraps all of the above and triggers lazy registry
  bootstrap when a consumer enters via `TypeService` first.

> **Note.** There is no `namespace` API. The word is reserved for
> things like file-system namespacing only; modelling 1C members must
> use *global property* / *library module* / *member descriptor*.

## JSON schema (`builtin-platform-types.json`)

`BuiltinPlatformTypesProvider` reads a JSON of `TypeDecl` records. Each
entry can set:

* `qualifiedNames` — canonical name + Ru/En aliases.
* `members` — properties and methods (`MemberDecl`).
* `exposedAsGlobal: true` — register the type as a **global property**
  (previously `"namespace"`).

## Memory characteristics

For ~10 M LOC codebases the dominant costs come from:

1. **Type identity** — addressed by interning `TypeRef` instances inside
   `TypeRegistry`. Symbols and the index hold only the lightweight
   `TypeRef` key, never a hydrated `Type`.
2. **Symbol → declared types** — `SymbolTypeIndex` keeps a single
   `ConcurrentHashMap<symbolKey, TypeSet>`; `TypeSet` is immutable and
   structurally shared (the same set of refs hashes to the same instance
   via `TypeSet.of(...)`).
3. **Inferred expression types** — kept in a Caffeine cache with weak
   keys. Keys are `ExpressionTreeNode` instances; on document re-parse
   the AST is rebuilt and old nodes become eligible for GC.

## Workspace scope notes

* Both `TypeRegistry` and `GlobalScopeProvider` are
  `@Scope("workspace")`. Each LSP workspace folder holds its own copy
  of types, configuration MDClasses, and OneScript library index — no
  cross-talk between roots.
* `GlobalScopeProvider.ensureGlobalsPublished()` is lazy and
  re-entrancy-safe: inside a workspace-scoped `@PostConstruct` it is
  illegal to resolve another workspace-scoped bean, so the publish
  step uses an `AtomicBoolean` toggle instead of an eager init.
* Tests that go straight into `GlobalScopeProvider` must trigger
  registry bootstrap first (call any `TypeService` method, or
  `typeRegistry.resolve("")`); otherwise platform providers will never
  run for that workspace.

## Tuning knobs

The Caffeine caches are configured at construction in
`ExpressionTypeInferencer`. Defaults are conservative; for very large
workspaces the following knobs may need tuning (currently constants in
the class — make them `@Value`-driven if needed):

* `maximumSize` of the per-expression cache. Default `100_000`.
  Increase if hot paths show repeated re-inference of the same nodes;
  decrease if heap pressure is high.
* `INFERENCE_DEPTH_LIMIT` in `InferenceContext`. Default `32`. Cycles
  in declared types (`a = b; b = a;`) are intercepted by the
  visited-symbols set, so this is a safety net.

## Benchmarking

Use `TypeServiceBenchmarkTest` (under `src/test/.../types/`). It:

* loads the existing fixture under `src/test/resources/types/`,
* warms up the inferencer on every variable in the symbol tree,
* repeats `findTypes(...)` N times and reports wall-clock per call.

```
./gradlew test --tests "*TypeServiceBenchmarkTest" --info
```

The output is informational; the test always passes. Capture
before/after numbers on the same machine for real performance work.

## Extension points

* **`PlatformTypesProvider`** — additional implementations can come
  from an external `platform-context` (full 1C platform members) or
  from a JSON of the OneScript syntax helper. Multiple providers may
  coexist; each can register members via `registerMemberSource` and
  expose new global properties via `registerAsGlobalProperty`.
* **`UserTypesProvider`** — currently registers OneScript class
  modules. Common modules are exposed as global properties carrying
  their export methods.
* **OneScript libraries** — `lib.config` (`<module>` / `<class>`) and
  conventional layouts (`Модули/`, `Классы/`, flat `.os`) are merged
  into the same `GlobalSymbolScope`.
* **Functional types (`sfaqer/lambdas`)** — not implemented in v2;
  the model intentionally leaves a slot for a future provider that
  would parse string-literal lambdas and produce a callable type.
