# Ход упорядочивания архитектуры

Журнал раундов: что сделано, чем проверено, какое ограничение против регрессии добавлено.
Решения — в [decisions.md](decisions.md), открытые задачи — в [backlog.md](backlog.md).

## Исходное состояние

- Карта зависимостей пакетов уже закреплена ArchUnit-правилом с whitelist'ом на каждый пакет;
  известный цикл — `references` ↔ `types`. Ацикличность пакетов вне ядра проверяется отдельно.
- Закреплены конвенции именования (диагностики, провайдеры, репортёры, команды, события),
  запрет stdout/stderr и `java.util.logging`, конструкторное внедрение.
- Инвентаризация энтропии (три независимых сканирования: утилиты, LSP-фичи,
  конфигурация/контекст/CLI/MCP) сведена в [backlog.md](backlog.md).

## Раунд 1. Поиск и чтение файла конфигурации

- **Проблема.** Три копии порядка поиска файла, две копии чтения JSON; битый JSON ронял
  загрузку конфигурации (ловился только `IOException`, а `JacksonException` в Jackson 3 —
  непроверяемое исключение).
- **Сделано.** `ConfigurationFiles.locate/read`; на него переведены
  `LanguageServerConfiguration`, `GlobalLanguageServerConfiguration`,
  `LanguageServerConfigurationFactory`. Удалены дубли маппера и цепочек `if`. См. ADR-0002.
- **Проверка.** Сначала красные тесты на битый и неверно типизированный JSON
  (`LanguageServerConfigurationTest`, `GlobalLanguageServerConfigurationTest`), затем
  контрактный `ConfigurationFilesTest`; тесты пакета `configuration`, `BSLLSBindingTest`,
  `ArchitectureTest` — зелёные.
- **Ограничение.** `ArchitectureTest.configuration_files_are_parsed_only_by_configuration_files`.

## Раунд 2. Кэш идентификаторов тестов и мёртвые `spring.cache.*`

- **Проблема.** `TestRunnerAdapter.getTestIds` кэшировался через `@Cacheable("testIds")` по
  `DocumentContext`, который равен другому по одному URI: после правки документа code lens'ы
  показывали прежний список тестов, записи удалённых документов не освобождались. Свойства
  `spring.cache.cache-names`/`spring.cache.caffeine.spec` (в `application.properties` и копией в
  `BSLLSBinding`) не действовали: собственный `@Primary CacheManager` отключает автоконфигурацию.
- **Сделано.** `TestRunnerAdapter` переведён на канонический механизм кэшей по документу —
  `AbstractDocumentLifecycleClearableIndex` (сброс на правку, перепривязку, закрытие, удаление,
  очистку) плюс сброс на смену конфигурации. Мёртвые свойства удалены.
- **Проверка.** Красный тест `TestRunnerAdapterTest.whenDocumentContentChanged_thenTestIdsAreRecomputed`
  → зелёный; тесты `codelenses`, `BSLLSBindingTest`, `CacheConfigurationTest`, `ArchitectureTest`.
- **Ограничение.** `ArchitectureTest.cacheable_methods_should_not_be_keyed_by_document_context`
  (проверено: на прежней реализации правило падает). В whitelist `CodeLenses` добавлен `Index`.

## Раунд 3. Мёртвый код

- **Сделано.** Удалены методы без единого вызова в `src/main` и `src/test`:
  `WorkspaceBeanScope.getAllInstances`, `ServerContextProvider.getDocumentUnsafeNoLock`, обе
  перегрузки `SemanticTokensHelper.addTokenRange`, `DiagnosticHelper.isCorrespondenceType`.
  Исправлено устаревшее имя `McpServerInfoCustomizer` → `McpServerInfoConfigurer` в комментариях.
- **Оставлено осознанно.** `CommandSupplier.needRefreshCodeLensesAfterExecuteCommand` — точка
  расширения plugin-интерфейса, симметричная обновлению inlay hints; не мёртвый код, а пока
  неиспользованная возможность. NPE при resolve code lens с неизвестным id не воспроизводим:
  все id зарегистрированы как подтипы Jackson, неизвестный id падает при разборе данных.
- **Проверка.** Компиляция; тесты `semantictokens`, `WorkspaceBeanScopeTest`,
  `ServerContextProvider*`, `ArchitectureTest`.

## Раунд 4. Состояние подсказок сложности

- **Проблема.** `AbstractComplexityInlayHintSupplier.enabledMethods` — `HashMap` с вложенными
  `HashSet`: пишется командой переключения (исполнитель workspace-сервиса), читается при
  построении подсказок (исполнитель text document) — гонка. Состояние не освобождалось при
  удалении документа и «переезжало» на документ, заведённый заново по тому же URI.
- **Сделано.** Конкурентные коллекции, атомарное переключение через `compute`, сброс по
  `ServerContextDocumentRemovedEvent`. Канонический `AbstractDocumentLifecycleClearableIndex`
  здесь не подходит намеренно: включённые методы обязаны переживать правку документа.
- **Проверка.** Красный тест `CognitiveComplexityInlayHintSupplierTest.testCleanupOnDocumentRemoved`
  (утечка состояния роняла и соседний тест) → зелёный; тесты `inlayhints`, `commands`.
- **Ограничение.** Машинной проверки нет: поле объявлено как `Map`, тип реализации ArchUnit не
  видит. Кандидат — статический анализ (Error Prone/SpotBugs), см. backlog.

## Раунд 5. Системы координат в `UnreachableCodeDiagnostic`

- **Проблема.** Диагностика смешивала две системы координат: позиция токена строилась с
  1-based строкой ANTLR и сверялась с 0-based диапазонами уже найденных блоков (`Ranges.create`),
  а границы веток препроцессора (`PreprocessorDeadCode.branches`) строились 1-based. Вложенный
  недостижимый код на последней строке найденного блока сообщался повторно, пересекающимся
  диапазоном.
- **Сделано.** Везде — LSP-координаты: `Positions.create(Token)` вместо ручной сборки,
  границы веток препроцессора переведены в 0-based.
- **Проверка.** Красный тест `UnreachableCodeDiagnosticTest.testNestedUnreachableCodeOnLastLineIsReportedOnce`
  → зелёный; исходная фикстура (18 диапазонов, включая ветки препроцессора) не изменилась.

## Раунд 6. Канонические `Positions`/`Ranges` вместо ручных копий

- **Сделано.** Ручная сборка LSP-позиции из токена (`getLine() - 1`, `getCharPositionInLine()`)
  заменена на `Positions.create/createEnd` в `FlowLayout`, `VariableTypeInlayHintSupplier`,
  `SourceDefinedMethodCallInlayHintCollector`, `InsertionWithoutChangeAndValidateDiagnostic`,
  `EventHandlerInvalidSignatureDiagnostic`. Удалён `RewriteMethodParameterDiagnostic.compare` —
  дословная копия `Ranges.compare(Position, Position)`.
- **Не тронуто.** Остальные `getLine() - 1` — арифметика над номерами строк (длины, отступы,
  индексы массивов строк), а не построение позиции; копии с иной семантикой (`encloses` без
  особого случая EOF) перечислены в backlog.
- **Проверка.** Тесты затронутых диагностик и `inlayhints`; поведение не менялось.
- **Ограничение.** Машинного запрета нет: ArchUnit не различает арифметику номеров строк и
  построение позиции.

## Раунд 7. URI в SARIF и наблюдаемость сбоев слушателей

- **Проблема 1.** `FileInfo.path` относителен каталогу `-w`, а `SarifReporter` строил URI через
  `Path.toUri()` — от рабочего каталога процесса. При `-w`, отличном от рабочего каталога, все
  URI в отчёте указывали не туда.
- **Сделано.** `ReportContext` получил `workspaceDir` — единственный источник «относительно чего
  заданы пути файлов»; `AnalyzeCommand` передаёт `-w`, SARIF разрешает путь от него. При
  умолчании (`-w .`) вывод не меняется — `ReporterOutputFormatTest` (побайтовая фиксация) зелёный.
- **Проблема 2.** `EventPublisherAspect` логировал исключение слушателя события как
  `e.toString()` — без стека: сбой в подписчике (индексы, провайдеры) был почти не диагностируем.
- **Сделано.** Исключение передаётся логгеру целиком.
- **Проверка.** Красные тесты `SarifReporterTest.fileUriIsResolvedAgainstWorkspaceDir` и
  `EventPublisherAspectListenerFailureTest` (закоммичены отдельно до исправления) → зелёные;
  тесты `reporters`, `aop`, `AnalyzeCommand*`, `ArchitectureTest`. Перед раундом прогнаны
  `types`, `diagnostics`, `cfg` — 519 тестовых классов, все зелёные.

## Раунд 8. Выбор режима запуска по подкоманде

- **Проблема.** `MainApplication` до старта Spring выбирал тип веб-приложения и профили по
  вхождению токена в любое место аргументов: `analyze -w <каталог>` (документированная опция
  рабочей области) считался websocket-режимом и поднимал servlet-контейнер на порту 8025;
  алиас `--websocket` не распознавался; токен `mcp` в значении любой опции включал MCP-профили.
- **Сделано.** `MainApplication.subcommandName` — единственное правило «какую подкоманду выберет
  picocli» (первый аргумент, совпавший с именем или алиасом подкоманды; значение `-c`
  пропускается). Имена и алиасы читаются из аннотаций `@Command` подкоманд — второй копии списка
  нет.
- **Проверка.** Красные `MainApplicationModeTest.modeIsTakenFromSubcommandNotFromAnyArgument` и
  `websocketAliasesSelectServletMode` → зелёные; `MainApplicationTest`, `ArchitectureTest`.

## Раунд 9. «Метод в позиции» — одна реализация в `SymbolTree`

- **Проблема.** Поиск метода, в котором стоит позиция, был написан пять раз:
  `ExpressionTypeInferencer`, `CompletionProvider`, `CompilationContextMemo`,
  `CompilationContextResolver` (inline), `InsertionWithoutChangeAndValidateDiagnostic` (свой цикл
  по родителям — тот же результат: методы BSL не вкладываются).
- **Сделано.** `SymbolTree.getMethodSymbolAtPosition(Position)` — рядом с
  `getSymbolAtPosition`, которым он пользуется; все пять мест переведены, приватные копии и
  ставшая сквозной обёртка `CompletionProvider.enclosingMethod` удалены.
- **Проверка.** Красный `SymbolTreeTest.getMethodSymbolAtPositionFindsMethodThroughNestedSymbols`
  (на заглушке) → зелёный; тесты `context.symbol`, `types.compilation`, `CompletionProvider*`,
  `InsertionWithoutChangeAndValidateDiagnosticTest`.

## Раунд 10. Одно имя для «ближайшего предка по правилу» в `Trees`

- **Проблема.** `Trees.getRootParent(ctx, int)` и `Trees.getAncestorByRuleIndex(ctx, int)`
  делегировали в один и тот же `org.antlr.v4.runtime.tree.Trees.getAncestor(ctx, int)` — по
  байткоду форка ANTLR это **ближайший** строгий предок. Javadoc и имя `getRootParent` обещали
  «самого верхнего»: два имени одной семантики, одно из них вводит в заблуждение (вызовов
  23 и 34 соответственно). Перегрузка с `Collection<Integer>` вела себя так же.
- **Сделано.** Каноническое имя — `getAncestorByRuleIndex` (для одного правила и для набора);
  обе двухаргументные перегрузки `getRootParent` удалены, 22 вызова переведены. Однаргументный
  `getRootParent(ctx)` действительно возвращает корень — оставлен, javadoc уточнён.
- **Проверка.** Компиляция; тесты `utils`, `context`, `references`, `semantictokens` и всех
  затронутых диагностик (103 тестовых класса).

## Раунд 11. Поиск предка: ручные циклы и «ancestor-or-self»

- **Сделано.** Три ручных цикла подъёма по родителям (`MagicNumberDiagnostic` ×2,
  `MagicDateDiagnostic`) заменены на `Trees.getAncestorByRuleIndex(…) != null`. Две копии
  «сам узел или ближайший предок» (`ExpressionAtPosition`, `NewExpressionReferenceFinder`)
  заменены новым `Trees.getAncestorOrSelfByRuleIndex`; пояснение, зачем родитель терминала
  считается кандидатом, перенесено к единственному месту, где оно нужно.
- **Проверка.** Красный `TreesTest.ancestorOrSelfConsidersNodeItselfThenAncestors` (на
  заглушке) → зелёный; тесты `references`, `types.inferencer`, `MagicNumber`/`MagicDate`.

## Раунд 12. Фильтр видов code action по LSP

- **Проблема.** Фильтр `only` проверялся в трёх местах тремя способами, и везде — точным
  сравнением строк. По LSP виды иерархичны: запрос `source` (меню «Source Action…») обязан
  включать `source.fixAll`, `refactor` — `refactor.rewrite` и т.п. Исправление всего файла по
  запросу `source` не возвращалось вовсе.
- **Сделано.** `CodeActionSupplier.isKindRequested` / `isKindExplicitlyRequested` — единственное
  место, где читается `only`; на них переведены `CodeActionProvider`, `AbstractQuickFixSupplier`,
  `SourceFixAllCodeActionSupplier`.
- **Проверка.** Красный `CodeActionProviderTest.testOnlyMatchesKindHierarchy` → зелёный; тесты
  code actions.
- **Ограничение.** `ArchitectureTest.code_action_kind_filter_is_read_only_by_canonical_predicate`:
  `CodeActionContext.getOnly()` вызывает только `CodeActionSupplier` (на промежуточном варианте,
  где провайдер читал `only` сам, правило падало).

## Раунд 13. Глубокий `QuickFixSupplier`

- **Проблема.** `QuickFixSupplier` отдавал двухшаговый API (`getQuickFixClass` →
  `getQuickFixInstance`), и каждый из трёх вызывающих повторял цепочку «найти класс → проверить
  → получить экземпляр → вызвать»; построение параметров fix-all (контекст с диагностиками и
  видом `quickfix`) было скопировано в `FixAllCodeActionSupplier` и `SourceFixAllCodeActionSupplier`.
- **Сделано.** Две операции, скрывающие поиск провайдера и параметры fix-all:
  `getQuickFixes(diagnostic, …)` и `getFixAllQuickFixes(code, diagnostics, …)`; старый API удалён,
  поставщики сократились до одного вызова.
- **Проверка.** `QuickFixSupplierTest` переписан под новый контракт (красный на заглушках →
  зелёный); тесты code actions и quick fix'ов.

## Раунд 14. Разложение выражения для метрик сложности

- **Проблема.** `CognitiveComplexityComputer` и `CyclomaticComplexityComputer` держали дословные
  копии `flattenExpression`/`flattenMember`/`flattenOperation` (~45 строк) — различие только в
  том, что когнитивная запоминала разобранные вложенные выражения.
- **Сделано.** Пакетный `BoolOperationSequence.of(expression, onExpression)` — одна реализация;
  когнитивная передаёт `ignoredContexts::add`, цикломатическая — пустой обработчик. Маркер
  `-1` получил имя `SPLITTER_TYPE`.
- **Проверка.** Тесты вычислителей, диагностик, code lens'ов и inlay hints сложности,
  `SmokyTest`, `ReporterOutputFormatTest` — значения метрик не изменились.
- **Найдено попутно.** Цикломатическая сложность не помечает вложенные выражения разобранными,
  поэтому операторы в скобках (`А И (Б ИЛИ В)`) засчитываются и при разборе внешнего выражения,
  и при входе обходчика во вложенное. Исправление меняет значения метрики — в backlog как
  ожидающее решения.
