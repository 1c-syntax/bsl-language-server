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
- **Проверено попутно.** Гипотеза о двойном счёте операторов во вложенных выражениях
  цикломатической сложностью (вложенные выражения не помечаются разобранными) опровергнута
  пробным тестом: `А И Б ИЛИ В` и `А И (Б ИЛИ В)` дают одно значение.

## Раунд 15. Параметры поставщиков code lens / inlay hints

- **Проблема.** Правило раздела `parameters` у поставщиков («нет записи или объект настроек —
  включён, `false` — выключен; настройки — в объекте») было записано пять раз: два одинаковых
  `supplierIsEnabled` (`CodeLensesConfiguration` — приватный, `InlayHintsConfiguration` —
  публичный, его звал провайдер), два одинаковых `readFlag` во флагах inlay hints и разбор
  `complexityThreshold` в `AbstractMethodComplexityCodeLensSupplier`.
- **Сделано.** `configuration.SupplierParameters.isEnabled/setting` — единственное толкование
  формата; все пять мест переведены, копии удалены. У диагностик правило другое (учитывает
  `activatedByDefault` и режим `mode`) — их не объединял.
- **Проверка.** Красный `SupplierParametersTest` (на заглушках) → зелёный; тесты
  `configuration`, `codelenses`, `inlayhints`, провайдеров.

## Сверка: полный `check`

- Полный прогон после раундов 1–15: 5903 теста, упал один —
  `CompletionContextFilteringTest.changingJsonConfigurationAppliesToTheNextRequestWithoutReparsing`
  ожидал `MismatchedInputException` от `update` с отвергнутым файлом. Это прямое следствие
  раунда 1: исключение заменено логом с сохранением прежних настроек. Суть теста (`null` для
  примитивной настройки не применяется, значение сохраняется) не изменилась; ожидание
  исключения заменено на «не бросает». Изменение контракта отражено в ADR-0002 и вынесено на
  подтверждение. `check -x test` (spotless, javadoc, jacoco) — зелёный.

## Раунд 16. Обработка запросов к документу в `BSLTextDocumentService`

- **Проблема.** 37 обработчиков запросов повторяли одно тело в 10–14 строк: найти документ по
  URI → при отсутствии вернуть ответ по умолчанию → выполнить обработку через
  `withFreshDocumentContext[Nullable]`; два последних метода были сквозными обёртками над
  третьим, различаясь только аннотацией nullability.
- **Сделано.** `onDocument(uri, ifMissing, request)` — единственное место этого протокола;
  обёртки слиты в один `withFreshDocumentContext` с `<T extends @Nullable Object>`. Обработчик
  запроса теперь — одно выражение; класс сократился с 1210 до 912 строк. Уведомления
  `didChange`/`didClose` со своей блокировкой оставлены как были. Ответы по умолчанию сохранены
  без изменений (в т.ч. непоследовательные `null`/пустой список — см. backlog).
- **Проверка.** Тесты `lsp`, `websocket`, `ArchitectureTest` (188 тестов); javadoc, spotless.
- **Ограничение.** `ArchitectureTest.text_document_requests_go_through_on_document`: в
  `BSLTextDocumentService` документ через `getDocumentUnsafe` ищут только `onDocument`,
  `didChange`, `didClose` (на прежней реализации правило нарушено 38 раз).

## Раунд 17. Дефолты настроек: код ↔ JSON-схема

- **Проблема.** Значения по умолчанию заданы в полях классов опций и повторены в `default`
  JSON-схемы конфигурации (подсказки редактора), без всякой сверки.
- **Сделано.** Контрактный `ConfigurationSchemaDefaultsTest`: обходит вложенность `properties`
  схемы и сверяет каждый скалярный и списковый `default` с сериализованной конфигурацией по
  умолчанию (workspace и глобальной). Первый прогон нашёл расхождение: у `traceLog` схема
  предлагала `""`, а в коде по умолчанию `null`. Это не косметика — подставленная редактором
  пустая строка превращается в `new File("")`, и сервер пишет ошибку «Can't create LSP trace
  file». `default` у `traceLog` из схемы удалён (документация и так говорит «по умолчанию не
  заполнено»). Второе отличие — порядок элементов множества `annotations` — артефакт; списки
  сравниваются по составу.
- **Ограничение.** Сам контрактный тест: новое поле опций с `default` в схеме, не совпадающим с
  кодом, роняет сборку. Документация (`ConfigurationFile.md`, обе локали) пока сверяется вручную.

## Раунд 18. Схема параметров диагностик ↔ код

- **Проблема.** `schema.json` (ссылки) и `parameters-schema.json` (определения параметров)
  генерируются задачей `updateJsonSchema` из `@DiagnosticParameter`, но хранятся в репозитории
  и перегенерируются вручную. Четыре диагностики (`CurrentRowAccess`,
  `DeprecatedHttpConnectionMethod`, `InsertionWithoutChangeAndValidate`,
  `SuspiciousChangeAndValidate`) в схеме отсутствовали — редактор не подсказывал и не
  проверял их параметры.
- **Сделано.** Схемы перегенерированы `updateJsonSchema` (вывод генератора принят как есть,
  включая `\uXXXX` в одной строке `schema.json` — эквивалентный JSON). Контрактный
  `DiagnosticParametersSchemaTest`: у каждой диагностики есть ссылка и определение, дефолт
  каждого параметра совпадает с кодом, в схеме нет описаний несуществующих диагностик.
- **Ограничение.** Сам тест; на прежних схемах он падает ровно на четырёх пропущенных
  диагностиках. Подсказка при падении — запустить `./gradlew updateJsonSchema`.

## Раунд 19. Документация диагностик: раскладка и шаблон

- **Проблема.** Страницы диагностик генерируются наполовину: автоматические блоки вставляет
  `updateDiagnosticDocs`, ориентируясь на заголовок раздела описания. У четырёх английских
  страниц (`CurrentRowAccess`, `DeprecatedHttpConnectionMethod`,
  `InsertionWithoutChangeAndValidate`, `SuspiciousChangeAndValidate`) заголовок был
  `## Diagnostic description` вместо шаблонного `## Description` — генератор дублировал шапку
  страницы. В `docs/en/diagnostics/` лежала страница `WrongMetadataInQuery.md` на русском языке
  для диагностики, которой в истории репозитория никогда не было (попала с посторонним merge).
- **Сделано.** Заголовки приведены к шаблону (генератор после этого изменений не даёт),
  страница-сирота удалена. Контрактный `DiagnosticDocsLayoutTest`: у каждой диагностики
  (`@DiagnosticMetadata`) есть страница в обеих локалях, страниц без диагностики нет, раздел
  описания назван по шаблону.
- **Проверка.** Тест красный на исходном состоянии (сирота) → зелёный; `DiagnosticInfosTest`.
- **Дополнительно.** `DiagnosticResourcesParityTest`: у каждого `*_ru.properties` диагностики
  есть `*_en.properties` с тем же набором ключей (сейчас 192 пары в паритете; проверено, что
  удаление ключа роняет тест).

## Раунд 20. Подвал справки CLI

- **Сделано.** Подвал `Copyright(c) …` был записан в семи `@Command` (в четырёх — 2018-2025,
  в трёх — 2018-2026). Теперь одна константа `cli.CommandHelp.FOOTER`.
- **Проверка.** Тесты `cli`, `MainApplication*`, `ArchitectureTest`.

## Раунд 21. Расширения файлов исходников

- **Сделано.** Расширения `bsl`/`os` были записаны в `BSLFiles` и дважды glob'ами в
  `BSLLanguageServer` (наблюдение за файлами и фильтры файловых операций). Источник —
  `FileType` (тип документа и так определяется по расширению через имя константы):
  добавлен `FileType.getExtension()`, оба списка glob'ов в `BSLLanguageServer` выводятся из него.
  `BSLFiles` (пакет `utils` не может зависеть от `context`) оставлен со своим списком, совпадение
  закреплено тестом `BSLFilesTest.listBslFilesFindsExactlyFileTypeExtensions`.
- **Проверка.** Тесты `lsp`, `BSLFilesTest`, `DocumentContextTest`.

## Раунд 22. Обновление подсветки при смене конфигурации

- **Проблема.** Code lens, inlay hints и диагностики по `LanguageServerConfigurationChangedEvent`
  просят клиента обновиться, семантические токены — нет, хотя подсветка строк-шаблонов зависит
  от `semanticTokens.strTemplateMethods`: после правки настройки подсветка оставалась прежней до
  правки файла.
- **Сделано.** `SemanticTokensProvider.handleConfigurationChanged` → `workspace/semanticTokens/refresh`
  (при поддержке клиентом).
- **Проверка.** Красный `SemanticTokensRefreshTest.semanticTokensRefreshOnLanguageServerConfigurationChange`
  → зелёный; тесты `semantictokens`, `ArchitectureTest`.

## Раунд 23. Обновление inlay hints по готовности индексов

- **Проблема.** Подсказки о типах переменных (`VariableTypeInlayHintSupplier` → `TypeService`) и
  о параметрах вызовов (индекс ссылок) считаются по данным, которые появляются после открытия
  документа: индекс ссылок — по `ServerContextPopulatedEvent`, типы конфигурации — по
  `ConfigurationTypesRegisteredEvent`. Семантические токены и диагностики на эти события
  обновляются, inlay hints — нет: подсказки, запрошенные до готовности, оставались неполными
  до правки файла.
- **Сделано.** `InlayHintProvider` отправляет `workspace/inlayHint/refresh` на оба события.
- **Проверка.** Красный `InlayHintRefreshTest` (на пустых обработчиках) → зелёный; тесты
  `inlayhints`, `lsp`, `ArchitectureTest`.
- **Дополнительно.** То же для code lens: линзы по индексам (`BeanUsagesCodeLensSupplier`)
  обновляются по `ServerContextPopulatedEvent` (красный
  `CodeLensProviderTest.testCodeLensRefreshesWhenServerContextIsPopulated` → зелёный).

## Раунд 24. `findMethodAt` в диагностиках обработчиков событий

- **Сделано.** Две дословные копии `findMethodAt` (`EventHandlerInvalidSignatureDiagnostic`,
  `EventHandlerOutsideEventRegionDiagnostic`) заменены каноническим
  `SymbolTree.getMethodSymbolAtPosition` (раунд 9): позиция — начало диагностики на
  объявлении метода, методы не вкладываются, результат тот же.
- **Проверка.** Тесты обеих диагностик, включая их quick fix'ы.

## Раунд 25. Дерево разбора «если текст есть»

- **Проблема.** «Получить AST, а без текста документа — пустой ответ» было написано пятью
  копиями: два приватных `safeGetAst` (`OpenDataObjectInference`, `ExpressionAtPosition`) и три
  inline `try/catch (DocumentContentUnavailableException)` (`KeywordReferenceFinder`,
  `NewExpressionReferenceFinder`, `SignatureHelpProvider`).
- **Сделано.** `DocumentContext.findAst(): Optional<FileContext>`; пять мест переведены, копии
  удалены. Два `safeGetAst` в `FormExpressionInference`/`PropertyMethodInference` оставлены: они
  намеренно глотают любой `RuntimeException` (с обоснованием в комментарии) — это другая
  политика, сужение её меняет поведение.
- **Проверка.** Красный `DocumentContextTest.findAstIsPresentOnlyWhileDocumentHasContent` (на
  заглушке) → зелёный; тесты `references`, `types.inferencer`, `SignatureHelp*`.

## Сверка: полный `check` после раундов 16–25

- 840 тестовых классов, 5922 теста, ни одного падения; spotless, javadoc, jacoco — зелёные.

## Раунд 26. Оставшийся мёртвый код

- **Сделано.** Удалён пустой слушатель `FormatProvider.handleEvent(LanguageServerConfigurationChangedEvent)`
  и поле `AbstractDiagnosticReporter.serverContextProvider`, которым не пользовался ни один
  наследник (из конструкторов `CodeQuality`/`GenericIssue`/`Sarif`-репортёров убран лишний
  параметр).
- **Проверка.** Тесты `reporters`, `formatting`, `FormatProvider*`, `AnalyzeCommand*`,
  `ArchitectureTest`.

## Раунд 27. Идентификатор поставщика

- **Проблема.** Правило «идентификатор поставщика = имя класса без суффикса роли с маленькой
  буквы» — протокольное: идентификатор служит ключом в `parameters` конфигурации и
  дискриминатором `id` в JSON данных линз/подсказок/аргументов команд. Оно было записано тремя
  копиями в `default getId()` у `CodeLensSupplier`, `InlayHintSupplier`, `CommandSupplier`.
- **Сделано.** `databind.SupplierIds.of(class, roleSuffix)` — единственная реализация
  (`databind` — единственный пакет, доступный всем трём фичам по карте зависимостей, и в нём уже
  живут помощники JSON-разбора данных поставщиков); `getId()` в трёх интерфейсах делегирует ему.
- **Проверка.** Красный `SupplierIdsTest` (на заглушке) → зелёный; тесты `codelenses`,
  `inlayhints`, `commands` и их провайдеров.

## Раунд 28. Разбор полезной нагрузки LSP-объектов

- **Проблема.** Разбор `data`/аргументов (уже объект своего класса, либо JSON после
  передачи клиенту) был написан четырьмя копиями: `CodeLensProvider`, `InlayHintProvider`,
  `CompletionProvider`, `CommandProvider`. Нагрузку-`Map` разбирала только копия автодополнения;
  остальные превратили бы её в `toString()` — не JSON.
- **Сделано.** `utils.LspPayloads.read(mapper, payload, type)`; четыре места переведены.
  Попутно ушли лишние `@SneakyThrows` (исключения Jackson 3 непроверяемые).
- **Проверка.** Красный `LspPayloadsTest` (на заглушке) → зелёный; тесты провайдеров, `lsp`,
  `codelenses`, `inlayhints`, `commands`, `ArchitectureTest`.

## Итог этапа

- **Финальная сверка.** Полный `check` после раундов 1–28: 842 тестовых класса, 5928 тестов, ни
  одного падения; spotless, javadoc, jacoco — зелёные.
- **Исправленные дефекты** (каждый — с красным тестом до исправления): битый JSON конфигурации
  ронял загрузку (1); устаревшие test id в code lens после правки (2); гонка и утечка состояния
  подсказок сложности (4); повторная диагностика недостижимого кода (5); URI в SARIF от
  рабочего каталога процесса вместо `-w` (7); `analyze -w` поднимал servlet-контейнер, `--websocket`
  не распознавался (8); фильтр видов code action не по LSP (12); схема предлагала
  ломающий дефолт `traceLog` (17); в схеме не было параметров четырёх диагностик (18);
  подсветка и подсказки не обновлялись после смены конфигурации и наполнения индексов (22–23).
- **Канонизация** (одна реализация вместо копий): поиск/чтение файла конфигурации,
  метод по позиции, ближайший предок по правилу, позиции из токенов, параметры поставщиков,
  идентификатор поставщика, разбор LSP-нагрузки, AST «если текст есть», протокол обработки
  запроса к документу, разложение выражения для метрик, quick fix'ы, подвал CLI, расширения
  файлов.
- **Машинные ограничения, добавленные на этапе**: 5 правил ArchUnit (чтение конфигурации,
  `@Cacheable` по `DocumentContext`, фильтр видов code action, обработчики запросов через
  `onDocument`, плюс расширение whitelist'а) и 5 контрактных тестов (дефолты схемы ↔ код,
  схема параметров диагностик ↔ код, раскладка документации диагностик, паритет ресурсов
  ru/en, расширения `BSLFiles` ↔ `FileType`).
- **Не сделано и почему** — в [backlog.md](backlog.md): решения мейнтейнера (контракты CLI и
  отчётов, цикл `references` ↔ `types`, пустые ответы LSP, политика ошибок `update`, правки
  `CLAUDE.md`), крупные изменения (общий компонент обновления клиента), а также рассмотренные и
  отклонённые варианты с обоснованием.

## Раунд 29. Разрыв цикла `references` ↔ `types`

- **Сделано.** См. ADR-0003: три finder'а на системе типов перенесены в `types.references`;
  `ReferenceIndexFiller` спрашивает про глобальные имена, self-типы модулей и библиотеки OneScript
  через SPI `references.WorkspaceNames` (реализация `types.registry.WorkspaceNamesImpl`).
  Пакет `references` больше ничего не импортирует из `types`.
- **Ограничение.** В карте слоёв `References` больше не может зависеть от `Types`; `references` и
  `types` вошли в правило ацикличности срезов. Попутно исправлен устаревший комментарий: ядро
  исключено из этого правила не из-за циклов (их нет), а из-за того, что top-level срез склеил бы
  `diagnostics` с листом `diagnostics.metadata`.
- **Проверка.** Тесты `references`, `types.references`, `types.registry`, `Hover*`,
  `AssignToReadOnlyPropertyDiagnosticTest`, `ArchitectureTest` (119 классов).

## Раунд 30. Разбор путей от MCP-клиента

- **Проблема.** `mcp/CLAUDE.md` требует разбирать значения от клиента только через
  `McpWorkspaceFolders`, но `McpDocumentReader` разбирал путь документа сам
  (`startsWith("file:") ? Absolute.uri : Absolute.uri(new File(…))`) — без починки windows-вида
  `file://D:\…`, которую рабочие папки проходят. Такой URI документа падал сырой ошибкой разбора
  вместо подсказки о регистрации рабочей папки.
- **Сделано.** `McpWorkspaceFolders.toDocumentUri` рядом с `toWorkspaceFolderUri` (общая починка
  и определение схемы); `McpDocumentReader` переведён на него.
- **Проверка.** Красные `McpWorkspaceFoldersTest.documentUri*` (на заглушке) → зелёные; тесты `mcp`.
- **Ограничение.** `ArchitectureTest.mcp_client_paths_are_normalized_by_workspace_folders`: в `mcp`
  строковые `Absolute.uri/path` вызывает только `McpWorkspaceFolders` (на прежнем
  `McpDocumentReader` правило нарушено).

## Итог второго захода (раунды 29–30)

- Разорван единственный разрешённый цикл карты слоёв (`references` ↔ `types`, ADR-0003); в
  карте не осталось допущенных циклов, а `references`/`types` вошли в правило ацикличности.
- Разбор путей от MCP-клиента сведён в `McpWorkspaceFolders` и закреплён правилом ArchUnit.
- Полный `check` после раунда 29 — 842 тестовых класса без падений; раунд 30 проверен тестами
  `mcp` и `ArchitectureTest` (21 правило).
- Оставшиеся пункты backlog — либо решения мейнтейнера (контракты CLI/отчётов, ответ на запрос к
  неизвестному документу, язык сообщений, политика ошибок `update`, статический анализ,
  правки `CLAUDE.md`), либо рассмотренные и отклонённые с обоснованием варианты.

## Раунд 31. Ответ навигации под возможности клиента

- **Сделано.** Понижение `LocationLink` → `Location` для клиентов без `linkSupport` было
  скопировано в `DefinitionProvider` и `ImplementationProvider`; теперь это
  `NavigationLinks.forClient(links, linkSupport)` рядом с прочими навигационными ссылками.
- **Проверка.** Красный `NavigationLinksTest` (на заглушке) → зелёный; тесты `Definition*`,
  `Implementation*`, `ArchitectureTest`.

## Раунд 32. Готовые канонические помощники в document link и document highlight

- **Сделано.** `TypeDefinitionDocumentLinkSupplier` собирал цель ссылки `uri#Lстрока,колонка`
  вручную — теперь `NavigationLinks.toTarget`. `If`/`TryStatementDocumentHighlightSupplier`
  держали приватные копии «сам узел или предок с правилом» — заменены на
  `Trees.getAncestorOrSelfByRuleIndex`.
- **Проверка.** Поведение не меняется (переход на уже покрытые тестами функции); тесты
  `documentlink`, `documenthighlight`, `ArchitectureTest` — 109 тестов зелёные.

## Раунд 33. Мёртвая ветка поиска конструктора в signature help

- **Проблема.** `SignatureHelpProvider.resolveConstructor` искал конструктор среди членов типа по
  именам `Constructor`/`ПриСозданииОбъекта`/`OnObjectCreation` (последнее — не то имя: канон в
  `Methods.isOscriptClassConstructorName` — `OnObjectCreate`). Ветка недостижима:
  `OScriptModuleMembersProvider.collectMembers` исключает конструктор из членов, а платформенные
  ресурсы методов с такими именами не объявляют. Конструктор всегда приходил из
  `TypeService.getConstructors`.
- **Сделано.** Ветка удалена — у конструктора один источник.
- **Проверка.** Характеризующий `SignatureHelpProviderOScriptLibraryTest.signatureForLibraryClassConstructor`
  и прочие тесты signature help (78) зелёные.

## Раунд 34. Диагностики на готовых канонических функциях

Источник — сканирование дублей в `diagnostics/` (пункты, где канон уже есть и открыт).

- **Сделано.**
  - `FormDataToValue`, `WrongUseOfRollbackTransactionMethod` (два места),
    `WrongUseFunctionProceedWithCall` повторяли тело `AbstractFindMethodDiagnostic.checkGlobalMethodCall`
    своим `MESSAGE_PATTERN` (тем же, что отдан в `super`) — теперь `super.checkGlobalMethodCall`,
    как у `SetPrivilegedMode`/`DisableSafeMode`.
  - `IncorrectUseOfStrTemplate` объявлял заново три шаблона `MultilingualStringAnalyser`
    (имя `СтрШаблон`, имя `НСтр`, плейсхолдеры) — использует открытые оригиналы и `isNStrCall`.
  - `ScheduledJobHandler`, `MissingEventSubscriptionHandler` искали метод общего модуля линейным
    `equalsIgnoreCase` — теперь `SymbolTree.getMethodSymbol(String)` (тот же результат: без учёта
    регистра, первый объявленный).
- **Проверка.** Поведение не меняется; тесты шести диагностик (13) зелёные.

## Раунд 35. Имя области программного интерфейса — `Regions`

- **Проблема.** «Это область `ПрограммныйИнтерфейс`/`Public` (и `СлужебныйПрограммныйИнтерфейс`/
  `Internal`)?» решалось четырьмя копиями: регулярками в `PublicMethodsDescription`,
  `NonExportMethodsInApiRegion`, `CachedPublic` (своя сборка из `Keywords`) и `TreeSet` без учёта
  регистра в `CommonModuleMissingAPI`, тогда как канонические шаблоны уже лежали закрытыми в
  `Regions`.
- **Сделано.** `Regions.isPublicRegionName` и `Regions.isApiRegionName` поверх существующих
  `PUBLIC_REGION_NAME`/`INTERNAL_REGION_NAME`; все четыре диагностики переведены, копии удалены.
- **Проверка.** Красные `RegionsTest.publicRegionName*`/`apiRegionName*` (на заглушках) → зелёные;
  тесты четырёх диагностик зелёные (49 тестов).

## Раунд 36. «ЭтотОбъект» и переменная параметра — по одному месту в `context`

- **Сделано.**
  - `Modules.isThisObject(TerminalNode)` (принимает `null`); прежняя перегрузка для
    `ComplexIdentifierContext` делегирует в неё. `RedundantAccessToObject` (два места) и
    `ThisObjectAssign` больше не держат своих шаблонов `ЭтотОбъект|ThisObject`.
  - `MethodSymbol.getParameterVariable(ParameterDefinition)` — переменная параметра среди дочерних
    символов метода (по началу имени). Побайтные копии `getVariableByParameter` в
    `TransferringParametersBetweenClientAndServer` и `RewriteMethodParameter` удалены.
- **Найдено попутно.** У `ThisObjectAssignDiagnostic` не было тестового класса (единственная
  диагностика без него, хотя фикстура лежала). Добавлен характеризующий тест — зелёный и на
  прежней реализации.
- **Проверка.** Красные `ModulesTest` и `SymbolTreeTest.parameterVariable*` (на заглушках) →
  зелёные; тесты пяти диагностик зелёные.
- **Ограничение.** `DiagnosticTestLayoutTest`: у каждой диагностики есть `XxxDiagnosticTest` —
  наследник `AbstractDiagnosticTest` (без нового теста правило красное: `ThisObjectAssign`).

## Раунд 37. Константа-аргумент и комментарий в диапазоне

- **Сделано.**
  - `AbstractFindMethodDiagnostic.isCalledWithSingleConstant(ctx, tokenType)` — «вызов ровно с
    одним аргументом, начинающимся с константы `Истина`/`Ложь`». Цепочки-копии в
    `SetPrivilegedMode` (`FALSE()`) и `DisableSafeMode` (`getToken(tokenType, 0)`) удалены.
  - `Ranges.containsAnyToken(range, tokens)` — «хотя бы один токен целиком в диапазоне»;
    `EmptyCodeBlock` и `MissingCodeTryCatchEx` проверяют через него комментарии в блоке.
- **Проверка.** Красные `AbstractFindMethodDiagnosticTest` и `RangesTest.containsAnyToken*`
  (на заглушках) → зелёные; тесты четырёх диагностик зелёные (18 тестов вместе с контрактом
  `DiagnosticTestLayoutTest`).

## Раунд 38. Настройка диагностики без параметров (дефект)

- **Проблема (найдено при разборе общего базового класса для диагностик сложности).**
  `DiagnosticBeanPostProcessor` передаёт в `configure` карту из файла конфигурации как есть.
  `AbstractMultilingualStringDiagnostic.configure` читал `declaredLanguages` через `get` без
  значения по умолчанию: настройки вида `"MultilingualStringHasAllDeclaredLanguages": {}` (или с
  опечаткой в ключе) роняли диагностику NPE в `MultilingualStringAnalyser`.
  `MultilingualStringUsingWithTemplate` — так же.
- **Сделано.** `getOrDefault` с текущим значением — как в `UsingServiceTagDiagnostic`.
- **Проверка.** Красный `MultilingualStringHasAllDeclaredLanguagesDiagnosticTest.configurationWithoutDeclaredLanguagesKeepsDefault`
  (NPE) → зелёный.
- **Ограничение.** `DiagnosticConfigurationContractTest`: каждая диагностика принимает
  `configure(Map.of())` и после неё работает. На прежнем коде красный ровно по двум
  диагностикам многоязычных строк, по остальным — зелёный.
- **Попутно.** Общий `DiagnosticHelper.configureDiagnostic` ищет поле параметра через
  `getClass().getDeclaredField`, то есть только в самом классе: параметр, объявленный в
  абстрактном предке, через него не настраивается (`DiagnosticParameterInfo` при этом видит и
  поля предка). Сейчас такой параметр один, и его класс переопределяет `configure`, — живого
  дефекта нет; записано в backlog, учтено при выносе базового класса диагностик сложности.

## Раунд 39. Общий базовый класс диагностик сложности

- **Проблема.** `CognitiveComplexityDiagnostic` и `CyclomaticComplexityDiagnostic` — две копии
  ~100 строк: обход методов и тела модуля, сборка связанной информации. Различия — источник
  данных (`getCognitive…`/`getCyclomaticComplexityData`), значения по умолчанию и то, что
  когнитивная первой связанной информацией ставит имя метода.
- **Сделано.** `AbstractComplexityDiagnostic` держит общий алгоритм; наследники задают
  `complexityData()`, `complexityThreshold()`, `checkModuleBody()`, `relateMethodName()`.
  Параметры остались в наследниках: значения по умолчанию разные, а общий `configure` видит
  только поля самого класса (см. backlog).
- **Проверка.** Характеризующие тесты обеих диагностик (число замечаний и связанной информации —
  35 и 21) и контракты параметров/схемы/документации диагностик зелёные (34 теста).

## Раунд 40. Группы равных узлов — `Trees.groupsOfEqualNodes`

- **Проблема.** `IfElseDuplicatedCodeBlock` и `IfElseDuplicatedCondition` держали одну и ту же
  процедуру поиска повторов (поле-множество «уже найденных», двойной проход по списку,
  `Trees.equalNodes`) и сборку связанной информации; отличие — фильтр «два пустых блока не
  повтор» у первой.
- **Сделано.** Чистая функция `Trees.groupsOfEqualNodes(nodes, comparable)` — группы «первое
  вхождение + его повторы» с тем же порядком и теми же правилами, что у прежних копий
  (включая то, что узел, отвергнутый фильтром, начинает свою группу). Обе диагностики — без
  состояния, по группе на замечание.
- **Проверка.** Красные `TreesTest.groupsOfEqualNodes*` (на заглушке) → зелёные; тесты обеих
  диагностик (с проверкой связанной информации) зелёные.

## Контрольная точка: полный `check` после раунда 40

848 тестовых классов, 5946 тестов — без падений; spotless зелёный. (Javadoc `check` не
запускает — см. поправку в раунде 43.)

## Раунд 41. Терминал у курсора, ссылка-определение, типы аргументов

- **Сделано.**
  - `Trees.findTerminalNodeEndingAtPosition` — терминал, заканчивающийся у каретки (поиск на символ
    левее с проверкой конца). Копии в `SelectionRangeProvider` и `LinkedEditingRangeProvider`
    удалены. `DocumentHighlightProvider` ищет иначе (позиция слева как запасной вариант, без
    проверки конца) — оставлен.
  - `Reference.definitionOf(symbol)` — ссылка-определение символа на себя. Бин
    `rename/SymbolDefinitionReferenceFactory` (класс ради одной фабрики) удалён; `ReferencesProvider`
    строит местоположение объявления через него же.
  - `TypeService.argumentTypes(doc, args)` — типы фактических аргументов (пропущенный → `EMPTY`).
    Копии в `SignatureHelpProvider` и `PlatformMethodCallInlayHintCollector` удалены.
    `DereferenceMemberMatcher` работает по дереву выражений и отрезает хвостовые пропуски —
    другой контракт, оставлен.
- **Проверка.** Красные `TreesTest.terminalNodeEndingAtPosition*`, `ReferenceTest.definitionOf*`,
  `TypeServiceTest.argumentTypes*` (на заглушках) → зелёные; тесты rename, references, signature
  help, inlay hints, selection range, linked editing и `ArchitectureTest` — 243 теста зелёные.

## Раунд 42. Парная скобка и наборы модификаторов семантических токенов

- **Сделано.**
  - `documenthighlight/MatchingBrackets.find` (пакетный — нужен только подсветке) — парная скобка
    с учётом вложенности в обе стороны. `BracketDocumentHighlightSupplier` (круглые и квадратные
    скобки BSL) и `SDBLBracketDocumentHighlightSupplier` (круглые скобки запроса) держали по своей
    копии сканирования; различие в ответе сохранено — BSL подсвечивает скобку и без пары, SDBL нет.
  - `semantictokens/SemanticTokenModifierSets.of(defaultLibrary, static, async)` — общие наборы
    модификаторов. Таблицы констант (до восьми на класс) и выбор по флагам в шести supplier'ах
    удалены.
- **Проверка.** Красные `MatchingBracketsTest` и `SemanticTokenModifierSetsTest` (на заглушках) →
  зелёные; тесты `documenthighlight` (70) и семантических токенов (266) зелёные.

## Раунд 43. Линзы запуска и отладки теста — общий базовый класс

- **Проблема.** `RunTestCodeLensSupplier` и `DebugTestCodeLensSupplier` — две копии ~150 строк:
  линзы на тестовых методах, резолв в команду с командной строкой раннера. Различия —
  идентификатор команды, аргументы раннера, ключ заголовка, тип данных линзы и то, что отладка
  скрыта без аргументов отладки.
- **Сделано.** `AbstractTestMethodCodeLensSupplier` держит алгоритм; наследники задают
  `commandId()`, `titleKey()`, `commandArguments(options)`, `createData(...)` и (отладка)
  `isAvailable(options)`. Типы данных линз остались свои у каждого — по ним Jackson различает
  данные при резолве; общий у них только интерфейс `TestMethodData`.
- **Проверка.** Характеризующие тесты линз (81, включая `RunTest`/`DebugTest`) и `ArchitectureTest`
  зелёные; `javadoc` и `spotlessCheck` зелёные.
- **Поправка к журналу.** `./gradlew check` задачу `javadoc` не запускает (в CI она идёт отдельным
  workflow), поэтому записи «javadoc зелёный» при полных прогонах `check` (сверки после раундов
  16–25, 1–28 и 40) javadoc на деле не проверяли. Явный прогон в этом раунде нашёл битую ссылку,
  внесённую раундом 29: перенесённый в `types.references` `KeywordReferenceFinder` ссылался в
  javadoc на `AnnotationReferenceFinder` без пакета — исправлено полным именем. С этого раунда
  javadoc проверяется явно.

## Раунд 44. Строка параметра и метка типов в hover

- **Сделано.** `HoverParameters.appendParameter` (имя и тип, значение по умолчанию, описание) и
  `HoverParameters.typesLabel(types, displayName)` (имена типов через `" | "` без пустых).
  `PlatformMemberHoverBuilder` и `ConstructorHoverBuilder` держали копии обоих фрагментов;
  различаются они только тем, как берут имя параметра и отображаемое имя типа, — это теперь
  аргументы.
- **Проверка.** Красные `HoverParametersTest` (на заглушках) → зелёные; тесты hover (170) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 45. Поддержка многострочных семантических токенов — одно чтение

- **Сделано.** `SemanticTokensHelper.supportsMultilineTokens(InitializeParams)` — чтение
  `textDocument.semanticTokens.multilineTokenSupport`. Восьмистрочные цепочки-копии в
  `CommentSemanticTokensSupplier` и `BslDocSemanticTokensSupplier` удалены.
- **Проверка.** Красный `SemanticTokensHelperCapabilitiesTest` (на заглушке) → зелёный; тесты
  семантических токенов (267), `javadoc`, `spotlessCheck` зелёные.

## Раунд 46. Самый вложенный узел, содержащий позицию

- **Сделано.** `Trees.findInnermostEnclosing(tree, position, type)` и `Trees.encloses(ctx, position)`.
  `NewExpressionReferenceFinder` (самое вложенное `Новый …`) и `SignatureHelpProvider` (самый
  вложенный `doCall`) держали одинаковые рекурсивный поиск и проверку `encloses`, отличаясь только
  типом узла.
- **Проверка.** Красный `TreesTest.innermostEnclosingNode*` (на заглушке) → зелёный; тесты
  signature help, `NewExpression*`, `PlatformConstructor*`, `ReferenceResolver*`,
  `ArchitectureTest` (138) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 47. Сравнение начала токена с позицией

- **Сделано.** `Positions.compareStart(token, position)` — знак сравнения начала токена с позицией
  без создания объектов (горячие пути). Четыре копии с разными знаками сравнения —
  `KeywordCompletion.startsBefore` (`<`), `SignatureHelpProvider.tokenBefore` (`<`),
  `CodeBlockLookup.startsAfter` (`>`), `EventHandlerInvalidSignatureDiagnostic.isAfterPosition`
  (`>=`) — удалены. `SymbolTree.before` (позиция против позиции) теперь делегирует в
  `org.eclipse.lsp4j.util.Positions.isBefore` — та же логика.
- **Проверка.** Красный `PositionsTest.compareStart*` (на заглушке) → зелёный; тесты completion,
  signature help, `types.inferencer`, `SymbolTree*`, `EventHandlerInvalidSignature` (549) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 48. Кеш профилей компиляции методов — один

- **Проблема.** `CompletionMethodTargets` держал свою копию `MemberMethodProfiles` из `types`:
  тот же ключ `ProfileKey(uri, directive)` с тем же порядком и тот же
  `computeIfAbsent(… resolver.methodContext(…))`.
- **Сделано.** `MemberMethodProfiles` перенесён в `types.compilation` (рядом с
  `CompilationContextResolver`, чей результат он запоминает) и открыт; completion и
  `MemberAccessContexts` используют его.
- **Проверка.** Поведение не меняется; тесты completion, `MemberAccess*`, `types.compilation`,
  `UnavailableMemberCall` и `ArchitectureTest` (687) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Итог третьего захода (раунды 32–48)

- **Финальная сверка.** `./gradlew check javadoc` после раунда 48: 852 тестовых класса, 5959 тестов,
  ни одного падения; javadoc и spotless зелёные (javadoc запущен явно — `check` его не включает).
- **Исправленные дефекты** (с красным тестом до исправления): падение многоязычных диагностик на
  настройке без `declaredLanguages` (раунд 38); битая ссылка javadoc после раунда 29 (раунд 43).
- **Новые ограничения:** `DiagnosticTestLayoutTest` (у каждой диагностики свой тест),
  `DiagnosticConfigurationContractTest` (каждая диагностика принимает настройку без параметров).
- **Канонизация:** 20+ копий сведены к функциям `Trees`, `Ranges`, `Positions`, `Regions`,
  `Modules`, `MethodSymbol`, `Reference`, `TypeService`, `HoverParameters`,
  `SemanticTokensHelper`, `MemberMethodProfiles`, к базовым классам `AbstractComplexityDiagnostic`
  и `AbstractTestMethodCodeLensSupplier`; удалены бин `SymbolDefinitionReferenceFactory` и
  недостижимая ветка signature help.
- **Остаток backlog** — решения мейнтейнера (контракты CLI и отчётов, ответ на запрос к
  неизвестному документу, язык сообщений, политика ошибок `update`, статический анализ,
  `Асинх`/`Ждать` в `CanonicalSpellingKeywords`, правки `CLAUDE.md`) и рассмотренные и
  отклонённые с обоснованием варианты.

## Раунд 49. Карта архитектуры и обзор недостающих областей

- **Сделано.** `docs/contributing/architecture/map.md` — модули верхнего уровня с ответственностью и
  контрактом, фактический граф зависимостей (собран из `import`: для каждого каталога —
  `grep -rhoE "^import com\.github\._1c_syntax\.bsl\.languageserver\.[a-z_]+"` с уникализацией),
  владельцы изменяемого состояния и их сброс, перечень машинно-проверяемых стандартов.
- **Проверено по графу.** Рёбра `context`/`configuration` → `diagnostics` идут только в словарь
  `diagnostics.metadata` (лист) — цикла по коду нет; ребро `references` → `types` отсутствует
  (ADR-0003).
- **Области «UI/дизайн-система» и «права доступа»** к серверу языка в обычном смысле не относятся
  (нет UI; нет пользователей и ролей). По доступу найдено одно: сетевые режимы открыты всем
  интерфейсам без проверки источника — в backlog как решение мейнтейнера (высокий приоритет).

## Раунд 50. Обработка ошибок: молчаливые потери диагностической информации

Источник — обзор областей «обработка ошибок / журналирование / конфигурация».

- **Сделано.**
  - `CacheConfiguration.createEhcacheManagerWithRetry`: если все каталоги персистентного кеша
    заняты, менеджер молча переходил на кеш в памяти (а `StatusTransitioner` заглушён в
    `application.properties`) — потеря персистентности не оставляла следа. Теперь WARN с базовым
    путём, числом попыток и последней причиной.
  - `BslContextHolder.load`: ошибка чтения синтакс-помощника журналировалась одним сообщением —
    стек терялся. Теперь исключение передаётся в журнал целиком.
- **Проверка.** Красные `CacheConfigurationTest.testCreateEhcacheManagerWithRetry_FallbackToInMemory`
  (нет WARN) и `BslContextHolderTest.loadFailureIsLoggedWithItsCause` (нет throwable) → зелёные.
- **Рассмотрено и отклонено:** перевод двух ручных `LoggerFactory.getLogger` на `@Slf4j` — Lombok
  генерирует тот же байт-код, правилом ArchUnit стиль не закрепить, выигрыш — формальное
  единообразие; `System.out` в аварийном пути `ParentProcessWatcher` — срабатывает после выхода
  родительского процесса, когда stdout уже никто не читает, и класс осознанно исключён из правила
  о стандартных потоках.

## Раунд 51. `SymbolTypeIndex` не забывал удалённые документы (дефект)

Источник — обзор области «кеширование и владение состоянием».

- **Проблема.** `SymbolTypeIndex` (workspace) держит карты по `MethodSymbol` и по URI, но слушал
  только правку, перепривязку и наполнение. На удаление документа записи не стирались:
  символы методов удалённого файла, а через `getOwner()` и его `DocumentContext`, жили в индексе
  до конца жизни рабочей области. `MethodReturnTypeIndexer` при этом рассчитывает, что значения
  «стираются им же по тому же событию».
- **Сделано.** Обработчик `ServerContextDocumentRemovedEvent` → `clear(uri)`. На закрытие и
  освобождение вторичных данных записи по-прежнему не стираются — объявленные типы читаются по
  дереву символов, которое их переживает (поэтому индекс и не на общей базе
  `AbstractDocumentLifecycleClearableIndex`, которая чистит и по ним).
- **Проверка.** Красный `SymbolTypeIndexDeclaredTypesTest.removedDocumentLeavesNoRecords` (на
  заглушке обработчика) → зелёный; тесты `types.index` и `MethodReturnType*` зелёные.

## Раунд 52. Типы удалённых `.os`-файлов оставались в реестре (дефект, утечка)

- **Проблема.** `OScriptModuleMembersProvider` регистрирует каждый `.os`-документ типом и
  держит источник членов — замыкание над `DocumentContext`. Снятие (`unregister`) вызывалось
  только из `OScriptLibraryIndex.handleDocumentRemoved`, который для небиблиотечного файла
  выходит сразу. Поэтому тип обычного удалённого `.os` оставался в реестре вместе с документом;
  javadoc утверждал обратное. Хуже всего — `LambdaStringTokenizer`: на каждый запрос
  семантических токенов он заводит временный документ `virtual-lambda-<UUID>.os`, и каждый
  такой документ оставлял в реестре тип, а регистрация источника сбрасывала memo членов всех
  типов.
- **Сделано.** `OScriptModuleMembersProvider` сам слушает `ServerContextDocumentRemovedEvent` и
  снимает свои регистрации — у них один владелец; явный вызов из `OScriptLibraryIndex` удалён,
  javadoc `unregister` исправлен.
- **Проверка.** Красный `OScriptModuleMembersProviderTest.removedPlainOsDocumentUnregistersItsType`
  (на заглушке) → зелёный; тесты `types.oscript`, семантических токенов и `OScript*` (570) зелёные.

## Раунд 53. BSL-модуль удалённого и созданного заново файла отдавал старые члены (дефект)

- **Проблема.** `ConfigurationModuleMembersProvider` регистрировал источник членов модуля
  замыканием над `DocumentContext` и на удаление документа не реагировал. Файл, удалённый и
  созданный заново по тому же пути (например, при переключении ветки), — новый
  `DocumentContext`, но та же пара «URI, тип»: регистрация шла по короткому пути «уже есть» и
  только сбрасывала memo, а источник продолжал читать дерево **удалённого** документа. Члены
  модуля оставались прежними до перезапуска; удалённый документ жил в реестре.
- **Сделано.** На каждый URI — одна регистрация, чей источник читает **текущий** документ URI.
  Удаление документа обнуляет его в регистрации (члены пропадают, ссылки на удалённый документ
  нет) и снимает связь URI → тип в `GlobalScopeProvider`; созданный заново файл привязывается к
  той же регистрации — источник не задваивается (в том числе платформенные члены общего модуля).
  Пробный `TypeRegistry.removeMemberSource` по итогу не понадобился и удалён.
- **Проверка.** Красный `ConfigurationModuleMembersProviderTest.removedAndRecreatedModuleServesMembersOfTheCurrentDocument`
  (после удаления оставались методы удалённого файла) → зелёный; тесты `types.*`, completion,
  hover, `ReferenceIndex*` (2950) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 54. Охраняющие условия, скомпилированные до регистрации типов (дефект)

- **Проблема.** `GuardConditionNarrowing` запоминает скомпилированные условия по документу, а имя
  типа в `ТипЗнч(Х) = Тип("…")` разрешает через реестр при компиляции. Сбрасывался кеш только по
  жизненному циклу документа. Условие, разобранное до регистрации конфигурационных типов,
  оставалось без утверждения, и сужение не работало до правки файла — хотя
  `InferredExpressionTypeIndex` по тому же событию сбрасывается (и пересчитывал бы сужение,
  получая из этого кеша прежнее пустое условие).
- **Сделано.** Полный сброс кеша по `ConfigurationTypesRegisteredEvent` и
  `ServerContextPopulatedEvent` (переменные условия разрешаются через индекс ссылок) — так же, как
  у `InferredExpressionTypeIndex`.
- **Проверка.** Красный `GuardConditionNarrowingCacheTest` (на заглушке условие бралось из
  запомненного) → зелёный; тесты `types.inferencer` и `GuardNarrowing*` зелёные; `javadoc`,
  `spotlessCheck` зелёные.

## Раунд 55. Мёртвые настройки журнала и копия выбора исполняемого файла

- **Сделано.**
  - Из `application.properties` удалены уровни журнала для hibernate, hikari, Spring Data/JPA и
    `LocalVariableTableParameterNameDiscoverer`: таких библиотек нет в `runtimeClasspath`
    (проверено `gradlew dependencies`), а класса нет в нынешнем Spring — строки ни на что не
    влияли.
  - `TestRunnerAdapter` выбирал исполняемый файл раннера сам (`SystemUtils.IS_OS_WINDOWS ? …`) —
    теперь `TestRunnerAdapterOptions.getExecutableForCurrentOS()`, как у линз.
- **Проверка.** Поведение не меняется; тесты `codelenses`, `MainApplication*`,
  `BSLLanguageServerTest` зелёные.

## Раунд 56. Кеш семантических токенов рос, пока документ открыт (дефект)

- **Проблема.** `SemanticTokensProvider.tokenCache` получал новый `resultId` на каждый полный
  запрос, а старый снимался только при успешной дельте. Клиент, не пользующийся дельтой, копил по
  массиву токенов всего документа на запрос — до закрытия документа.
- **Сделано.** На документ хранятся два последних результата: дельту клиент запрашивает от
  последнего полученного, а предпоследний нужен, когда ответ с последним до клиента не дошёл
  (запрос отменён после расчёта) — так же устроены существующие тесты дельты «v1 → полный v2 →
  дельта от v1». Более старые вытесняются; сброс по закрытию/удалению документа — как раньше.
- **Проверка.** Красный `SemanticTokensProviderTest.onlyRecentResultsOfDocumentAreKept` (на
  прежнем коде дельта от вытесненного результата считалась) → зелёный; тесты семантических
  токенов (259), `javadoc`, `spotlessCheck` зелёные. Первая версия (один результат на документ)
  уронила три теста дельты — по ним и выбран размер истории.

## Раунд 57. Автодополнение на документе без текста отвечало пустым списком (дефект)

- **Проблема.** В `CompletionProvider` три копии сканирования строки под курсором
  (`dotCompletionInfo`, `currentLineInfo`, `isDotCompletion`) были обёрнуты в
  `catch (Exception) { return null/false; }`. Тела бросать могли лишь одно —
  `DocumentContentUnavailableException` (текста нет: документ закрыт и освобождён), — и его
  глушили: клиент получал пустой список («предложить нечего») вместо «содержимое изменилось»,
  которым отвечают остальные запросы (канонический путь `onDocument`, раунд 16) и после которого
  клиент переспрашивает.
- **Сделано.** Одно сканирование `currentLineInfo` без перехвата; признак «после точки» —
  `LineInfo.afterDot()`; `dotCompletionInfo` и `isDotCompletion` выводятся из него.
- **Проверка.** Красный `BSLTextDocumentServiceTest.completionAfterCloseFailsAsContentModified`
  (пустой список) → зелёный; тесты completion и `BSLTextDocumentServiceTest` (419) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 58. Имя файла конфигурации — одна константа

- **Проблема.** `.bsl-language-server.json` было записано строкой в пяти местах Java-кода: дефолты
  `@Value` у `GlobalLanguageServerConfiguration`, `LanguageServerConfiguration` и
  `LanguageServerConfigurationFactory`, инициализаторы полей `LanguageServerConfiguration`,
  константа `ConfigurationFileSystemWatcher`. Смена имени требовала бы правки каждого, а
  наблюдатель за файлами разошёлся бы с поиском файла молча.
- **Сделано.** `ConfigurationFiles.DEFAULT_FILE_NAME` (владелец поиска и чтения файла); все места
  ссылаются на неё (в `@Value` — конкатенацией констант). `application.properties` остаётся
  внешним источником Spring-свойств.
- **Ограничение.** `ConfigurationFilesTest.defaultFileNameLiteralLivesOnlyInConfigurationFiles`:
  скан `src/main/java` — имя файла строкой встречается только в `ConfigurationFiles` (на прежнем
  коде красный по четырём файлам).
- **Найдено попутно (backlog).** Наблюдатель при незаданном файле узнаёт новый файл конфигурации
  по имени по умолчанию, а не по `app.configuration.path`: при переопределённом пути созданный
  позже файл не подхватится без перезапуска.

## Контрольная точка: `./gradlew check javadoc` после раунда 58

853 тестовых класса, 5967 тестов — без падений; javadoc и spotless зелёные.
