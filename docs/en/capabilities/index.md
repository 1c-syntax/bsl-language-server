# BSL Language Server features

A catalog of the 1C:Enterprise (BSL) and OneScript language server features in an LSP-capable editor (demos captured in VS Code / code-server). Each feature lives in its own file.

## Features

- [Code completion](completion.md) — Context-aware suggestions as you type: global functions, object methods and properties (with type inference), types after the `New` operator, keywords and local variables.
- [Go to definition](definition.md) — Jump from a usage to the declaration of a procedure, function, variable or method. Works within a module and across configuration modules.
- [Go to implementations](implementation.md) — For OneScript classes using the `extends` inheritance library: jump from an interface method (`&Интерфейс`) to the same-named methods in every implementing class (`&Реализует`).
- [Find references](references.md) — Find all usages of a symbol across the project.
- [Quick documentation (hover)](hover.md) — Hovering over a symbol shows its signature, type and the description from doc comments.
- [Signature help](signatureHelp.md) — While typing a method call, shows the parameter list and highlights the active parameter.
- [Diagnostics](diagnostics.md) — Highlights errors, potential issues and coding-standard violations inline and in the Problems panel.
- [Code actions / Quick fixes](codeAction.md) — Offers automatic fixes for diagnostics and refactorings via a shortcut at the problem location.
- [Formatting](formatting.md) — Format the whole document, a selection, or on-the-fly while typing (indentation, keyword casing).
- [Rename](rename.md) — Safely rename a symbol together with all its usages.
- [Linked editing](linkedEditing.md) — Editing the declaration of a local symbol (variable, parameter) updates all of its occurrences in the module at once — without invoking rename.
- [Document symbols / Outline](documentSymbol.md) — A tree of the module's procedures, functions and regions — in the Outline view and quick navigation.
- [Workspace symbols](workspaceSymbol.md) — Quickly jump to any method or object across the whole project by name.
- [Document highlight](documentHighlight.md) — Placing the cursor on a symbol highlights all its occurrences in the current module.
- [Call hierarchy](callHierarchy.md) — Who calls a method and what it calls — as an expandable tree.
- [Type hierarchy](typeHierarchy.md) — For OneScript classes using the `extends` inheritance library: a tree of supertypes and subtypes derived from the `&Расширяет` and `&Реализует` annotations.
- [Code folding](folding.md) — Collapse procedures, functions, regions and blocks for easier navigation.
- [Smart selection](selectionRange.md) — Expand and shrink the selection step by step along syntactic boundaries.
- [Semantic highlighting](semanticTokens.md) — Accurate highlighting based on code analysis: distinguishes variables, parameters, methods and annotations.
- [Inlay hints](inlayHint.md) — Inline hints embedded in the code — for example, parameter names at call sites.
- [Code lens](codeLens.md) — Informational lines above procedures: cognitive and cyclomatic complexity, test run and coverage.
- [Colors: preview and picker](color.md) — Color preview for `Новый Цвет(...)` and `WebЦвета.*`. Clicking the swatch opens the picker — choosing a color updates the code. Web colors convert to/from the RGB constructor representation.
- [Document links (hyperlinks)](documentLink.md) — Clickable links right in the module text: `См.`/`See` references in doc comments jump to the mentioned method or object; URLs in comments open in the browser; and optionally (**off by default**, `documentLink.showDiagnosticDescription`) the diagnosed range itself becomes a link to the diagnostic's documentation.
