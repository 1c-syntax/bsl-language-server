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
  **Подготовка рабочей области идёт в фоне.** Типы конфигурации регистрирует `@Async`-обработчик
  `WorkspaceAddedEvent` (`ConfigurationTypesProvider`) на однопоточном `workspacePreparationExecutor`;
  следом `ServerContextProvider` ставит туда же задачу контекста (`trackPreparation`), и подготовка
  закончена, когда исполнитель до неё доберётся. До тех пор `getConfiguration()` при непрочитанных
  метаданных отвечает пустой конфигурацией, а не ждёт чтения (настоящие — `awaitConfiguration()`),
  документы, заведённые за это время, потом перепривязываются, `populateContext` ждёт конца
  подготовки (`awaitPreparation()`). Кто читает типы или метаданные сразу после `addWorkspace`
  без наполнения (CLI `format`, MCP, тесты), сам зовёт `awaitPreparation()`.
  **`getScriptVariantLanguage()`** — язык исходников проекта (ScriptVariant конфигурации,
  фолбэк на язык сервера). Единственное место этого расчёта: его спрашивают и там, где
  документа нет (регистрация типов), а `DocumentContext` делегирует сюда, добавляя одну
  свою поправку — OS-файл.
- **`DocumentContext`** — состояние **одного файла** (prototype-bean). Тяжёлые вычисления
  **ленивые** и потокобезопасные (`Lazy<T>` + `ReentrantLock`):
  - *первичные* (зависят от контента): AST `BSLParser.FileContext`, токены, `SymbolTree`;
  - *вторичные* (кэшируются, сбрасываются при изменении файла): диагностики, метрики,
    `ComplexityData`, данные подавления диагностик, SDBL-запросы;
  - *привязка к конфигурации*: `ModuleType`, MD-объект, `mdoRef` — от содержимого не зависят и
    при изменении файла не сбрасываются. Пересчитываются только у документа, заведённого, пока
    метаданные ещё читались (`rebindToConfiguration`): дерево символов строится заново по тому
    же AST, текст заново не разбирается.
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
- **`DiagnosticComputer`** — **интерфейс** (`compute(DocumentContext) → List<Diagnostic>`), не
  `Computer<T>`. Реализация (`DefaultDiagnosticComputer`) живёт в пакете `diagnostics` и
  прогоняет все `BSLDiagnostic` параллельно, фильтруя по конфигурации и правилам подавления.
  Так `context` не зависит от движка диагностик (инверсия зависимости).
- **`DiagnosticIgnoranceComputer`** → данные о подавлении (сканирует `// BSLLS:…-off`).
- **`CyclomaticComplexityComputer`** / **`CognitiveComplexityComputer`** → `ComplexityData`.
- **`QueryComputer`** → токенизированные SDBL-запросы; **`GitBlameComputer`** → git blame.

## События — `events/`

Spring `ApplicationEvent`: `ServerContextDocumentAdded/Removed/Closed/ClearedEvent`,
`DocumentContextContentChangedEvent`, `DocumentContextConfigurationBindingChangedEvent`,
`ServerContextPopulatedEvent`, `ConfigurationTypesRegisteredEvent`. Используются downstream-индексами (`references/`, `types/`)
для инвалидации кэшей. События жизненного цикла workspace (`Workspace(Before)Added/RemovedEvent`)
вынесены в корневой пакет `events` и «похудели» до URI (контекст в payload не носят), чтобы от них
мог зависеть `configuration`, не образуя цикл `configuration↔context`; подписчики резолвят контекст
сами через `ServerContextProvider`.

**Разбор документа ≠ правка.** Тот же самый текст перечитывается заново после освобождения
вторичных данных (в пакетном анализе — у каждого файла). `DocumentContext` держит отпечаток
разобранного содержимого, переживающий освобождение, и выставляет
`isContentChangedOnLastRebuild()`; признак уходит в `DocumentContextContentChangedEvent`
(`isContentChanged()`). Слушатель, чьи записи привязаны к **символам**, по перечитыванию не
делает ничего: символы построенного заново дерева равны прежним (имя, документ, позиция имени),
поэтому находятся по старым записям, а снос и сборка заново оставили бы окно, в котором чужой
поток не находит уже посчитанного (см. ишью 4429). Сбрасываться по перечитыванию обязаны только
те, чьи ключи — узлы AST: у них ключи после разбора другие.

**Перепривязка — третий вид изменения.** Документ, заведённый до прочтения конфигурации, после
него перепривязывается (`ServerContext.rebindDocument`): AST тот же, дерево символов построено
заново, публикуется `DocumentContextConfigurationBindingChangedEvent`. Здесь символы прежним
**не равны**, если сменился их вид: класс символа входит в `equals`, а обычный метод мог стать
обработчиком события или конструктором. Поэтому слушатели, чьи записи привязаны к символам или к
привязке документа, на это событие пересчитывают документ — в том же порядке, что и на правку.
Индексы на `AbstractDocumentLifecycleClearableIndex` сбрасывают по нему записи документа.

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
