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
