<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# context/ — контекст сервера, документов и символы

Модель разобранных исходников 1С и доступ к ним. Здесь живут рабочая область (workspace),
состояние отдельного файла, дерево символов и «вычислители» (computers). См. корневой
[CLAUDE.md](../../../../../../../../../CLAUDE.md) для общей картины.

## Контекст (этот пакет)

- **`ServerContextProvider`** — глобальный синглтон, маршрутизирует несколько workspace'ов;
  держит карту `URI → ServerContext` для O(1) поиска документа.
- **`ServerContext`** — одна рабочая область: карта `URI → DocumentContext`, жизненный цикл
  документов (add/remove/rebuild/clear), кэш метаданных конфигурации 1С (`CF` через `Lazy`,
  Caffeine-кэш общих модулей). Мутации публикуют Spring-события (через AOP).
- **`DocumentContext`** — состояние **одного файла** (prototype-bean). Тяжёлые вычисления
  **ленивые** и потокобезопасные (`Lazy<T>` + `ReentrantLock`):
  - *первичные* (зависят от контента): AST `BSLParser.FileContext`, токены, `SymbolTree`;
  - *вторичные* (кэшируются, сбрасываются при изменении файла): диагностики, метрики,
    `ComplexityData`, данные подавления диагностик, SDBL-запросы;
  - *инвариантные*: `ModuleType`, MD-объект, `mdoRef`.
  - `freezeComputedData()` / `unfreeze…` управляют сбросом вторичных данных (открытый
    документ — «разморожен» для редактирования; закрытый — «заморожен» ради экономии памяти).
- **`DocumentChangeExecutor`** — применяет инкрементальные `textDocument/didChange`:
  копит изменения в очереди по версии, батчит, по опустошении очереди перестраивает документ.
- **`FileType`** — `BSL` или `OS` (OneScript). **`MetricStorage`** — метрики модуля
  (процедуры/функции, строки, NCLOC, комментарии, операторы, сложность).

## Символы — `symbol/`

Модель символов исходника: методы, переменные, области, их иерархия.

- **`Symbol`** — базовый контракт (имя, `SymbolKind`, deprecation, приём визитора).
  **`SourceDefinedSymbol`** — символ, привязанный к коду: владелец `DocumentContext`, `Range`,
  `selectionRange`, родитель/дети.
- **`SymbolTree`** — неизменяемое дерево с лениво-кэшируемыми индексами (методы/переменные по
  имени, регионы, конструктор); `getSymbolAtPosition(Position)`, `getMethodSymbol(name)` и т.п.
- **`SymbolTreeVisitor`** — обход дерева (`visitModule`, `visitRegion`, `visitRegularMethod`,
  `visitConstructor`, `visitVariable`).
- Виды символов: `ModuleSymbol` (корень), `MethodSymbol`/`RegularMethodSymbol`,
  `ConstructorSymbol` (конструктор OneScript-класса), `RegionSymbol`, `VariableSymbol`
  (реализации `Short/IntBasedVariableSymbol`, `AnnotatedVariableSymbol`), `ParameterDefinition`.
- Mixin-интерфейсы: **`Describable`** (описание символа → `SymbolDescription`,
  `ParserSymbolDescriptionAdapter` для BSL-doc комментариев), **`Exportable`** (`isExport()`).

## Вычислители — `computer/`

Интерфейс **`Computer<T>`** (`compute(): T`). Вызываются лениво из `DocumentContext`.

- **`SymbolTreeComputer`** → `SymbolTree`; делегирует `ModuleSymbolComputer`,
  `MethodSymbolComputer`, `RegionVariableSymbolComputer`/`VariableSymbolComputer`.
- **`DiagnosticComputer`** (Spring-компонент, **не** `Computer<T>`) — прогоняет все
  `BSLDiagnostic` (параллельно), фильтрует по конфигурации и правилам подавления.
- **`DiagnosticIgnoranceComputer`** → данные о подавлении (сканирует `// BSLLS:…-off`).
- **`CyclomaticComplexityComputer`** / **`CognitiveComplexityComputer`** → `ComplexityData`.
- **`QueryComputer`** → токенизированные SDBL-запросы; **`GitBlameComputer`** → git blame.

## События — `events/`

Spring `ApplicationEvent`: `ServerContextDocumentAdded/Removed/Closed/ClearedEvent`,
`DocumentContextContentChangedEvent`, `ServerContextPopulatedEvent`,
`Workspace(Before)Added/RemovedEvent`, `ConfigurationTypesRegisteredEvent`. Используются
downstream-индексами (`references/`, `types/`) для инвалидации кэшей.

**Эти события публикуются не вручную, а через AOP** — аспект `aop/EventPublisherAspect`
(AspectJ compile-time weaving) перехватывает вызовы методов-мутаторов `ServerContext`/
`DocumentContext` (по пойнткатам в `aop/Pointcuts`) и публикует событие в нужный Spring-контекст.
Поэтому: чтобы появилось новое событие — добавь метод-мутатор под пойнткат/новый advice в аспекте,
а не вызывай `publishEvent` из бизнес-логики. Там же — аспекты `MeasuresAspect` (замеры,
`@ConditionalOnMeasuresEnabled`) и `SentryAspect` (отправка исключений в Sentry).
Подписка downstream — через `@EventListener` (исполняется в правильном workspace-контексте).

## Правки в этом каталоге

- Новые тяжёлые вычисления делай **ленивыми** через `Computer<T>` + `Lazy`-поле в `DocumentContext`,
  не считай в конструкторе.
- Если расширяешь модель символов — не забудь про `SymbolTreeVisitor` и индексы в `SymbolTree`.
- Кэши downstream-подсистем инвалидируются по событиям из `events/` — при новом виде изменений
  данных проверь, что нужное событие публикуется.
