<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# providers/ — провайдеры возможностей LSP

Каждая возможность LSP реализована отдельным провайдером. Провайдеры — это «тонкий» слой:
принимают `DocumentContext` + параметры запроса LSP4J, делегируют в подсистемы и возвращают типы
LSP4J. См. корневой [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Общая форма

- Spring `@Component` + `@RequiredArgsConstructor` (инъекция через конструктор). Общего
  базового класса/интерфейса нет; имя `XxxProvider` соответствует запросу LSP (`HoverProvider` →
  `textDocument/hover`).
- Вызываются из **`BSLTextDocumentService`** (документные запросы) и **`BSLWorkspaceService`**
  (`workspace/symbol`, `workspace/executeCommand`) — оба в родительском пакете.

## Кто за что отвечает

- Навигация/поиск: `DefinitionProvider`, `ReferencesProvider`, `ImplementationProvider`,
  `CallHierarchyProvider`, `TypeHierarchyProvider`, `DocumentHighlightProvider`,
  `LinkedEditingRangeProvider`, `SelectionRangeProvider`.
- Информация/подсказки: `HoverProvider`, `CompletionProvider`, `SignatureHelpProvider`,
  `InlayHintProvider`, `CodeLensProvider`, `DocumentLinkProvider`, `ColorProvider`.
- Структура/редактирование: `DocumentSymbolProvider`, `SymbolProvider` (workspace symbols),
  `FoldingRangeProvider`, `SemanticTokensProvider`, `FormatProvider`, `RenameProvider`,
  `CodeActionProvider`, `CommandProvider`.
- Диагностики: `DiagnosticProvider` — поддерживает **обе** модели: push
  (`computeAndPublishDiagnostics` → `textDocument/publishDiagnostics`) и pull (LSP 3.17+,
  `getDiagnostic` → `DocumentDiagnosticReport`); выбор по capability клиента; реагирует на события
  смены конфигурации/наполнения контекста.

## Делегирование в подсистемы

- → [`references/`](../references/CLAUDE.md): `Definition`, `References`, `Rename`,
  `CallHierarchy`, `LinkedEditingRange` (через `ReferenceResolver`/`ReferenceIndex`).
- → [`types/`](../types/CLAUDE.md): `Completion`, `Hover`, `SignatureHelp` (через `TypeService`);
  `Implementation`, `TypeHierarchy` (через отношения типов / extends-библиотеку).
- → диагностики: `DiagnosticProvider` через `documentContext.getDiagnostics()`.
- → [`context/symbol/`](../context/CLAUDE.md): `DocumentSymbol`, `Symbol` и поиск символа по позиции.

## Паттерн «supplier/registry»

Часть провайдеров расширяема набором suppliers (новая фича = новый supplier-бин, без правки
провайдера): `CodeLensProvider`↔`CodeLensSupplier`, `InlayHintProvider`↔`InlayHintSupplier`,
`DocumentLinkProvider`↔`DocumentLinkSupplier`, `CodeActionProvider`↔`CodeActionSupplier`,
`DocumentHighlightProvider`↔`DocumentHighlightSupplier`, `ColorProvider`↔`Color*Supplier`,
`CommandProvider`↔`CommandSupplier`, `HoverProvider`↔`MarkupContentBuilder` (диспетч по классу
символа). У `CodeLens`/`InlayHint`/`Completion` — ленивый `resolve` (данные тянутся по запросу).

## Правки в этом каталоге

- Держи провайдер тонким: бизнес-логику — в подсистему (`references`/`types`/`context`), провайдер
  только адаптирует под LSP4J и под capability клиента.
- Где есть реестр suppliers — добавляй новый supplier, а не разрастай провайдер.
- Проверь, что новый метод вызывается из `BSLTextDocumentService`/`BSLWorkspaceService`.
