<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# references/ — индекс и резолюция ссылок на символы

Две задачи: **индексация** (обойти AST → заполнить индекс вхождений) и **резолюция**
(определить символ под курсором). На этом строятся провайдеры definition, references, rename,
call hierarchy. См. корневой [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Индексация

- **`ReferenceIndex`** (Spring-компонент, workspace-scoped) — in-memory индекс вхождений.
  Запись: `addMethodCall`, `addModuleReference`, `addVariableUsage`. Запрос:
  `getReferencesTo(SourceDefinedSymbol)`, `getReferencesFrom(URI|symbol)`,
  `getReference(URI, Position)`, `clearReferences(URI)`. Учитывает доступность (export/правила
  конструкторов) через `isReferenceAccessible()`. Хранилище — в `model/`
  (`SymbolOccurrenceRepository`, `LocationRepository`, `AnnotationRepository`).
- **`ReferenceIndexFiller`** — обходит ANTLR-дерево (`BSLParserBaseVisitor`) и наполняет индекс.
  Триггеры: `DocumentContextContentChangedEvent` → `fill(DocumentContext)`;
  `ServerContextDocumentRemovedEvent` → `clearReferences()`. Внутри —
  `MethodSymbolReferenceIndexFinder` (вызовы методов, ссылки на модули/классы, обработчики
  `NotifyDescription`) и `VariableSymbolReferenceIndexFinder` (использования переменных с флагом
  definition/reference, паттерны общих модулей). Имена интернируются (lowercase).

## Резолюция под курсором

- **`ReferenceResolver`** — диспетчер: по очереди (по `@Order`) опрашивает `ReferenceFinder`'ы,
  возвращает первое совпадение. Метод `findReference(URI, Position)`.
- **`ReferenceFinder`** (`Optional<Reference> findReference(uri, position)`) — реализации:
  - `ReferenceIndexReferenceFinder` — делегат к `ReferenceIndex.getReference()` (сорсовые символы);
  - `SourceDefinedSymbolDeclarationReferenceFinder` — попадание курсора в диапазон объявления;
  - `NewExpressionReferenceFinder` — `Новый Тип(...)` → конструктор через `TypeService`;
  - `PlatformMemberReferenceFinder` — платформенные/конфигурационные члены через
    `TypeService.memberAt()` → `PlatformMemberSymbol`;
  - `AnnotationReferenceFinder` — аннотации (.os), синтетический `AnnotationSymbol`;
  - `KeywordReferenceFinder` — ключевые слова BSL, синтетический `KeywordSymbol`.

## Модель — `model/`

- **`Reference`** (public record) — результат запроса: `from` (`SourceDefinedSymbol`, где встречена
  ссылка), `symbol` (цель — сорсовый или синтетический), `uri`, `selectionRange`, `occurrenceType`.
  Фабрики `Reference.of(...)`, хелперы `getSourceDefinedSymbol()`, `toLocation()`.
- **`Symbol`** (record) — лёгкий ключ индекса (mdoRef, moduleType, scopeName, kind, name),
  интернируется, `Comparable`. **`SymbolOccurrence`** — одно вхождение
  (`OccurrenceType` REFERENCE|DEFINITION + symbol + `Location`). **`Location`** — URI + диапазон.
- Репозитории (workspace-scoped): `SymbolOccurrenceRepository` (Symbol → отсортированное
  множество вхождений), `LocationRepository` (URI → вхождения), `AnnotationRepository`.

## Правки в этом каталоге

- Новый вид ссылки «под курсором» — это новый `ReferenceFinder` с подходящим `@Order`, а не
  правка `ReferenceResolver`.
- Новый вид индексируемого вхождения — расширение `ReferenceIndexFiller` (обход AST) и метода
  добавления в `ReferenceIndex`; не забудь про инвалидацию по событиям документа.
- Различай **синтетические** символы (keyword/platform/annotation, создаются на лету) и
  **сорсовые** (`SourceDefinedSymbol` из `context/symbol/`).
