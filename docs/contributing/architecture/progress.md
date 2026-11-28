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

## Раунд 59. Очистка одной рабочей области снимала отметки другой (дефект)

- **Проблема.** `ReferenceIndexFiller` — синглтон, а набор `filled` («документ проиндексирован»)
  один на все рабочие области, тогда как данные индекса — per-workspace репозитории. При
  удалении рабочей области A обработчик делал `filled.clear()` — снимал отметки и B. Если
  регистрация типов B заканчивалась позже, её документы не получали повторной индексации
  self-членов до правки (узкая гонка multi-root).
- **Сделано.** На очистке контекста отметки снимаются только у его документов: на первом
  наполненном документе — у всех оставшихся документов этого контекста, вместе с разовой
  очисткой индекса; следующие события находят свою отметку уже снятой и ничего не делают —
  без квадратичного прохода.
- **Проверка.** Красный `ReferenceIndexFillerWorkspacesTest` (отметки B стирались) → зелёный;
  тесты `references`, `ServerContextProvider*`, `MultiWorkspace*` (197) зелёные.

## Раунд 60. Второй парсер JSON (Jackson 2) — удалён

- **Проблема.** Проект читает JSON Jackson 3 (`tools.jackson`), но `KeywordMetadataLoader`
  импортировал `com.fasterxml.jackson.databind`/`core` — Jackson 2, приходящий транзитивно. Две
  библиотеки для одной задачи с разной политикой исключений: у Jackson 2 ошибка разбора — это
  `IOException`, у Jackson 3 — непроверяемое `JacksonException`.
- **Сделано.** Загрузчик переведён на Jackson 3; поведение «битый ресурс → пустые метаданные»
  сохранено перехватом `IOException | JacksonException` (как у `ConfigurationFiles`, раунд 1).
- **Проверка.** Характеризующий `KeywordMetadataLoaderTest.malformedResourceReturnsEmpty` зелёный и
  до, и после перехода.
- **Ограничение.** `ArchitectureTest.no_classes_should_use_jackson_2` — запрет зависимостей от
  `com.fasterxml.jackson.databind..`/`core..` (аннотации `com.fasterxml.jackson.annotation` у
  Jackson 3 свои и разрешены); на прежнем коде красный ровно по этому загрузчику.

## Раунд 61. Встроенные ресурсы `types/registry` — через `BuiltinResources`

- **Сделано.** `BuiltinQueryTables` открывал свой ресурс в обход `BuiltinResources.open`
  (`getResourceAsStream` + собственная проверка на `null`) — теперь так же, как остальные
  загрузчики пакета; отсутствие ресурса по-прежнему даёт пустой список и WARN (в сообщение
  добавлен путь ресурса).
- **Проверка.** Тесты `BuiltinQueryTables*`, `QueryTable*`, `Sdbl*` зелёные.

## Раунд 62. Быстрое исправление `UselessTernaryOperator` не работало у клиента (дефект)

Источник — обзор области «API и контракты данных».

- **Проблема.** Исправление читало `data` диагностики проверкой
  `instanceof DiagnosticAdditionalData`. Внутри сервера это объект, а в запросе code action
  клиент присылает диагностику обратно, и LSP4J отдаёт `data` JSON-деревом — проверка не
  проходила, и исправление по реальному LSP не предлагалось. Тест передавал объекты в памяти и
  этого не видел; остальные потребители нагрузок уже читают её через `LspPayloads.read`.
- **Сделано.** `data` читается через `LspPayloads.read` (объект или JSON-дерево).
- **Проверка.** Красный `UselessTernaryOperatorDiagnosticTest.quickFixIsBuiltFromDiagnosticDataSentBackByClient`
  (`data` — Gson-дерево, как от клиента: исправлений 0) → зелёный; `ArchitectureTest`,
  тесты code action зелёные.

## Раунд 63. Путь файла в JSON-отчёте — от каталога проекта (дефект)

- **Проблема.** `FileInfo.path` относителен каталогу проекта (`-w`), а `JsonReporter` писал его
  как есть: Jackson сериализует `Path` через `toUri()`, то есть разрешает относительный путь от
  рабочего каталога процесса. При `-w`, отличном от него, «полный путь к файлу» (так в
  `docs/reporters/json.md`) указывал не туда — та же ошибка, что раунд 7 исправил у SARIF.
- **Сделано.** `JsonReporter` берёт `workspaceDir` из `ReportContext` и пишет путь, разрешённый от
  него. При умолчании (`-w .`) вывод прежний — побайтовый `ReporterOutputFormatTest` зелёный.
- **Проверка.** Красный `JsonReporterTest.filePathIsResolvedAgainstWorkspaceDir` (путь вёл в
  рабочий каталог процесса) → зелёный; тесты `reporters` и `AnalyzeCommand*` (16 классов)
  зелёные.
- **Остальные находки обзора контрактов** меняют видимый вывод (TSLint, SARIF/Generic, важность,
  JUnit, CodeQuality, схема `codeLens`, MCP, CLI) — в backlog как решения мейнтейнера.

## Раунд 64. Переключатели линз тестов — в схеме и документации

- **Проблема.** `codeLens.parameters` принимает `runTest`, `runAllTests`, `debugTest` (идентификаторы
  supplier'ов линз тестов), но их не было ни в `schema.json`, ни в `ConfigurationFile.md` обеих
  локалей: редактор с подсказками по схеме их не предлагал, а документация молчала.
  `ConfigurationSchemaDefaultsTest` ходит только от схемы к коду и такого не видит.
- **Сделано.** Три ключа добавлены в схему и в обе локали документации (описания сверены с
  кодом: «Запустить все тесты» стоит над первым методом файла, «Отладить тест» показывается
  только при заданных `testRunner.debugTestArguments`).
- **Ограничение.** `SupplierIdsSchemaTest`: набор ключей `codeLensParameters`/`inlayHintParameters`
  в схеме равен набору идентификаторов бинов-supplier'ов (направление «код → схема» и обратно);
  на прежней схеме красный ровно по трём ключам.

## Контрольная точка: `./gradlew check javadoc` после раунда 64

855 тестовых классов, 5973 теста — без падений; javadoc и spotless зелёные.

## Итог четвёртого захода (раунды 49–64)

- **Карта архитектуры** (`map.md`): модули, фактический граф зависимостей, владельцы состояния,
  перечень машинных проверок.
- **Обзор недостающих областей** — кеширование и владение состоянием, обработка ошибок и журнал,
  конфигурация, API и контракты данных, сетевой доступ.
- **Исправленные дефекты** (каждый — с красным тестом до исправления): утечки и устаревшие данные
  реестра типов при удалении документов (51–53), охраняющие условия до регистрации типов (54),
  рост кеша семантических токенов (56), автодополнение без текста (57), отметки индекса между
  рабочими областями (59), быстрое исправление по данным от клиента (62), путь в JSON-отчёте (63);
  молчаливые потери диагностики в журнале (50).
- **Новые ограничения:** `no_classes_should_use_jackson_2` (ArchUnit), `SupplierIdsSchemaTest`
  (идентификаторы supplier'ов ↔ схема), скан литерала имени файла конфигурации.
- **Остаток** — решения мейнтейнера (сетевая открытость, форматы отчётов, контракты MCP и CLI,
  Sentry, перезагрузка синтакс-помощника, правки `CLAUDE.md`) и рассмотренные и отклонённые с
  обоснованием варианты.

## Раунд 65. Контекст текущей рабочей области — `ServerContextProvider.getCurrentServerContext`

Источник — остаточное сканирование `types/`, `references/`, `context/`, `cfg/`, `formatting/`,
`folding/`, `color/`, `mcp/`, `cli/`, `lsp/` и доступа к данным (раунд 64): вне `types/` и одной
мелочи в `mcp/` существенных дублей не найдено; доступ к метаданным и файлам централизован
(`DocumentContext.getMdObject`, `ServerContext.findCommonModule`/`getDocument`, чтение файла — одно
место).

- **Проблема.** Единственный параллельный путь к данным: «контекст рабочей области текущего
  потока» — `WorkspaceContextHolder.get()` → проверка на `null` → `getAllContexts().get(uri)` →
  проверка на `null` — шестью копиями (`ConfigurationGenericExpander`, `QueryTableResolver`,
  `MetadataCollectionSpecializer`, `ConfigurationTypesProvider`, `MethodReturnTypeIndexer`,
  `DiagnosticProvider`); у `ConfigurationTypesProvider` — без проверки URI (на `null` бросил бы NPE).
- **Сделано.** `ServerContextProvider.getCurrentServerContext()` (`Optional`); копии удалены.
  Модульные тесты четырёх классов заглушали внутренний путь (`getAllContexts`) — переведены на
  новый метод; выбор рабочей области по потоку проверяет `ServerContextProviderTest`.
- **Проверка.** Красный `ServerContextProviderTest.currentServerContextIsTheWorkspaceSelectedInThread`
  (на заглушке) → зелёный; тесты `types.registry`, `MethodReturnType*`, `DiagnosticProvider*`,
  `QueryTable*`, `ArchitectureTest` (84 класса) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 66. Канонические помощники вместо копий в `types/` и `mcp/`

Источник — пункты 2, 6, 7, 8 остаточного сканирования (раунд 64). Новых помощников нет: везде
уже был канонический, копии появились до него или мимо него.

- **Self-тип модуля в `EventHandlerResolver`.** Своя карта «вид модуля → русское имя обёртки»
  и `mdoSpecificQualifiedName` повторяли `ConfigurationModuleMembersProvider.selfTypeQualifiedName`
  — того, кто этот тип регистрирует; разойтись они могли молча (обработчик не нашёл бы событие).
  Резолвер берёт имя у провайдера; семейства (`ModulePlatformTypeNames.familyName`) и особый
  случай общего модуля остались как были.
- **`Неопределено`.** Пять приватных копий `new TypeRef(PRIMITIVE, "Неопределено")`
  (`ReturnTypeFromBodyInference`, `OpenDataObjectInference`, `ExpressionTypeInferencer`,
  `GuardConditionNarrowing`, `FormParametersRegistrar`) ссылаются на `TypeRef.UNDEFINED`.
- **Поиск метода по имени** в `DeclaredParameterTypeResolver.localMethod` — линейный проход
  `getMethods()` вместо `SymbolTree.getMethodSymbol` (тот же поиск без учёта регистра, по
  упорядоченному массиву). Модульный тест заглушал `getMethods()` — переведён на канонический метод.
- **Первый возвращаемый тип члена** — три копии тела `MemberDescriptor.returnType()`
  (`CompletionProvider`, `GlobalMemberSearchTool`, `GlobalMemberInfoTool`) заменены вызовом.
- **Проверка.** Поведение не меняется, новых тестов нет; `types.inferencer`, `types.registry`,
  `*Completion*`, `mcp`, `*EventHandler*`, `ArchitectureTest` (1630 тестов) и `*Hover*`
  зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 67. Параметр метода по имени — `MethodSymbol.getParameterDefinition`

Источник — пункт 5 остаточного сканирования (раунд 64).

- **Проблема.** «Параметр метода с таким именем без учёта регистра» — восемь копий фильтра по
  `getParameters()`: пять в `DeclaredParameterTypeResolver` (два цикла и три потока, один из них
  через `anyMatch`), `SymbolTypeIndex.describedParameterTypes`,
  `VariableSymbolMarkupContentBuilder.docFieldIndex`, `AnnotationParamSymbolMarkupContentBuilder`.
  Обратный запрос («переменная параметра») уже был каноническим — `getParameterVariable`.
- **Сделано.** `MethodSymbol.getParameterDefinition(String)` рядом с `getParameterVariable`;
  копии удалены. Оставлены поиски, которым нужен ещё и **номер** параметра
  (`DeclaredParameterTypeResolver.typesOf` — для контракта события по позиции,
  `EventContractFormatter`), и сопоставление параметров **аннотации**
  (`OScriptMetaAnnotationResolver`) — это другой список.
- **Тесты.** `SymbolTreeTest.parameterDefinitionIsFoundByNameIgnoringCase` красный на заглушке →
  зелёный. Мок `MethodSymbol` в `DeclaredParameterTypeResolverTest` вызывает реальный метод по
  умолчанию (`thenCallRealMethod`) — иначе мок отвечает пустым.
- **Проверка.** `context.symbol`, `types.inferencer`, `types.index`, `types.oscript`, `*Hover*`,
  `*MarkupContentBuilder*`, `*AutumnNavigation*` (114 классов, 867 тестов) зелёные; `javadoc`,
  `spotlessCheck` зелёные.

## Раунд 68. Специализация дженерика по имени объекта — `TypeRegistry.findSpecialization`

Источник — пункт 3 остаточного сканирования (раунд 64).

- **Проблема.** «У дженерика ровно один параметр → подставить имя объекта → найти
  зарегистрированную специализацию» — шесть копий: `CatalogOwnerTypesRegistrar`,
  `RegisterTypesRegistrar` (три: достройка членов, расширение записи, `specializedByName`),
  `ConfigurationTypesProvider.registerHierarchySuppressions`,
  `MetadataCollectionSpecializer.registerOwnMetadata`. Расходились в мелочи: три копии
  отбрасывали «специализацию», совпавшую с самим дженериком (подстановка не изменила имени),
  остальные нет — и вешали бы члены, подавления и расширения на сам дженерик, то есть на все
  объекты семейства сразу.
- **Сделано.** `TypeRegistry.findSpecialization(generic, argument)` с проверкой всех трёх
  условий; копии и `RegisterTypesRegistrar.specializedByName` удалены. Привязка параметра
  (`bindings`) там, где она нужна дальше (`registerMemberExpansion`, достройка членов регистра),
  строится после поиска. Не копия и не тронута — `registerEnumValueExpansion`: тип менеджера
  ей передан, специализацию она не ищет.
- **Тесты.** `TypeRegistrySpecializationTest.findSpecializationReturnsRegisteredSpecializationOfSingleParameterGeneric`
  красный на заглушке → зелёный (найдена; не зарегистрирована; не дженерик; подстановка
  вернула сам дженерик).
- **Проверка.** `types.registry`, `types.inferencer`, `*Completion*`, `*Hover*`,
  `ArchitectureTest` (160 классов, 1492 теста) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 69. Уточнение коллекции под её строку — `CollectionReturnsSpecializer.register`

Источник — пункт 4 остаточного сканирования (раунд 64), последний из найденных.

- **Проблема.** Регистрация уточнённых членов коллекции («найти строку таблицы значений →
  override коллекции поверх членов обобщённой с подменой строки и выгрузкой») — три копии:
  `FormDataTypesRegistrar.specializeCollectionReturns` (данные формы), `RegisterTypesRegistrar`
  (набор записей), `ConfigurationTypesProvider.registerTabularSectionPlatformMembers`
  (табличная часть). Наружу из `CollectionReturnsSpecializer` торчали две детали —
  `specialize` и `unloadedRow`, — которые каждый собирал сам.
- **Сделано.** `CollectionReturnsSpecializer.register(...)`; `specialize` и `unloadedRow` стали
  приватными — модуль углубился: снаружи одна операция вместо трёх шагов. Приватный
  `specializeCollectionReturns` в `FormDataTypesRegistrar` оставлен однострочным: у него четыре
  вызова и javadoc о том, зачем уточнение нужно любой коллекции данных формы.
- **Тесты.** Новый `CollectionReturnsSpecializerTest.registeredCollectionReturnsItsOwnRow`
  красный на заглушке → зелёный.
- **Проверка.** `types.registry`, `types.inferencer`, `*Completion*`, `*Hover*`,
  `ArchitectureTest` (161 класс, 1493 теста) зелёные; `javadoc`, `spotlessCheck` зелёные.
- **Итог остаточного сканирования раунда 64:** все восемь пунктов закрыты (65–69).

## Контрольная точка: `./gradlew check javadoc` после раунда 69

`BUILD SUCCESSFUL in 12m 20s`: 856 тестовых классов, 5977 тестов, падений нет.

## Раунд 70. `format` без найденных файлов — сообщение об ошибке

Источник — бэклог («завершается с кодом 1 без сообщения»).

- **Проблема.** Если путь существует, но BSL/OS-файлов в нём нет, `format` возвращал 1 молча:
  у несуществующего пути причина в журнале была, у пустого каталога — нет.
- **Сделано.** Сообщение уровня ERROR с переданными путями; код возврата 1 прежний (его
  закрепляет тест, и он часть поведения CLI).
- **Тесты.** `FormatCommandTest.callReturnsOneWhenNoFilesFound` дополнен проверкой записи в
  журнале — красный до исправления, зелёный после.
- **Проверка.** `FormatCommandTest` (8 тестов), `spotlessCheck` зелёные.

## Раунд 71. Точки внедрения «ОСени» — `AutumnComponentInferencer.injectionCandidates`

Источник — сканирование `codeactions/`, `types/oscript/` и др. после раунда 69 (бэклог, пункт 4).

- **Проблема.** «Какие объявления класса могут быть точками внедрения» — параметры конструктора
  и поля модуля, без повторов параметров среди переменных дерева (`VariableKind.PARAMETER`) —
  было записано дважды: индексом точек внедрения (обратная навигация «кто внедряет желудь») и
  code action перехода к производителю. Новый вид точки, добавленный в одном месте, потерялся бы
  в другом — переходы в обе стороны разошлись бы.
- **Сделано.** Статический `injectionCandidates(SymbolTree)` и запись `InjectionCandidate`
  рядом с `InjectedBean`; внедряется ли желудь, по-прежнему решает `injectedBean`. Статический
  — чтобы модульные тесты индекса, мокающие инференсер, видели настоящий перечень.
- **Тесты.** `AutumnComponentInferencerTest.injectionCandidatesAreConstructorParametersAndModuleFieldsOnce`
  красный на заглушке → зелёный.
- **Проверка.** `types.oscript`, `*Autumn*`, `codeactions` (35 классов, 270 тестов) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 72. Фиксация и отказ от файла отчёта — в `ReportFile`

Источник — бэклог, пункт 3 сканирования после раунда 69.

- **Проблема.** Шесть файловых репортёров повторяли окончание жизненного цикла: `endReport` —
  `commit()` и строка журнала «… report saved to …», `abortReport` — «закрыть, если файл
  открыт». Общий базовый класс не подходит: часть репортёров наследует
  `AbstractDiagnosticReporter`, часть — нет.
- **Сделано.** `ReportFile.commit(reportName)` фиксирует и пишет строку журнала (тот же текст),
  `ReportFile.discard(reportFile)` отказывается от начатого отчёта; безымянный `commit()` стал
  приватным. У репортёров ушли собственные логгеры — журналировать им больше нечего.
- **Тесты.** `ReportFileTest.commitWithReportNameAnnouncesWhereReportIsSaved` и
  `discardDeletesOpenedReportAndToleratesUnstartedOne` красные на заглушках → зелёные.
- **Проверка.** `reporters` (включая побайтовый `ReporterOutputFormatTest`), `AnalyzeCommandTest`
  (16 классов, 57 тестов) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 73. Типы параметров `.os`-метода — по имени, через `DescribedTypeResolver`

Источник — бэклог, пункт 1 сканирования после раунда 69.

- **Дефект.** `OScriptModuleMembersProvider.toMemberDescriptor` собирал типы членов класса из
  описания сам и сопоставлял строки секции «Параметры» с параметрами **по номеру**. Описание,
  перечисляющее параметры не в порядке сигнатуры или пропускающее один, давало параметру чужой
  тип; у BSL-модулей то же описание читалось верно — описание к параметру привязывает разбор
  метода, по имени (`ParameterDefinition.getDescription()`). Кроме того, у `.os` терялся тип
  элемента коллекции («Массив из Строка» → просто `Массив`) и не отсекалось пояснение за именем.
- **Сделано.** Типы параметров — `DescribedTypeResolver.parameterTypes`, как у BSL; у резолвера
  появился общий `types(List<TypeDescription>)`. Тип возврата `.os` по-прежнему — первая
  резолвящаяся запись (правило отличается от BSL — вынесено в бэклог как решение). Свой
  `resolveTypes` удалён.
- **Тесты.** Новый `OScriptMethodDescriptionTypesTest` — описание с переставленными параметрами
  и `Массив из Строка`: красный (первому параметру доставалось `Число`) → зелёный.
- **Проверка.** `types.oscript`, `types.registry`, `*Hover*`, `*Completion*`, `*SignatureHelp*`
  (150 классов, 1466 тестов) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 74. Тип прилепляемой коллекции «ОСени» — через `DescribedTypeResolver`

Источник — тот же пункт 1, вторая копия.

- **Дефект.** `AutumnCollectionIndex` резолвил тип возврата `Получить()` по сырому имени записи:
  «ФиксированныйМассив из Строка» в реестре не находилось, и тип коллекции оставался неизвестным
  (срабатывал запасной путь по имени коллекции). Типы элементов коллекции не разбирались вовсе.
- **Сделано.** Тип — `DescribedTypeResolver.types`, тот же разбор, что у параметров и
  возвращаемых значений BSL и `.os`. Индекс зависит от резолвера вместо реестра, свой
  `resolveTypes` удалён.
- **Тесты.** `AutumnCollectionIndexTest.getterReturnedTypeIsReadWithoutExplanationTail` красный →
  зелёный; прочие тесты индекса (имена без пояснения) не изменились.
- **Проверка.** `types.oscript`, `*Autumn*`, `codeactions` (36 классов, 272 теста) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 75. Индекс «имя → значения» с удалением вклада документа — `UriScopedNameIndex`

Источник — бэклог, пункт 2 сканирования после раунда 69.

- **Проблема.** Одна и та же пара карт — «имя в нижнем регистре → конкурентное множество» и
  обратная «URI → имена» для точечного удаления вклада документа — была написана четырежды:
  `AutumnBeanIndex`, `AutumnInjectionPointIndex` и дважды в `TypeRelationIndex` (родители и
  интерфейсы). Отличия — только во внешнем виде: у одних значение несёт свой URI, у
  `TypeRelationIndex` значение и есть URI, у родителя имя одно, а не множество.
- **Сделано.** `types/oscript/UriScopedNameIndex<V>` с функцией «значение → документ»: `add`,
  `get` (без учёта регистра), `namesOf(uri)`, `removeByUri`, `clear`. Все четыре копии сведены к
  нему; приведение регистра, удаление опустевших имён и разбор обратной карты ушли из индексов.
- **Тесты.** Новый `UriScopedNameIndexTest` — три поведенческих теста красные на заглушке →
  зелёные (`clear` на заглушке проходит вырожденно и сторожит реализацию).
- **Проверка.** `types.oscript`, `*Autumn*`, `codeactions`, `codelenses`, `*Implementation*`,
  `ArchitectureTest` (56 классов, 364 теста) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 76. Остаток сканирования: javadoc `Trees.encloses`, копия в `ModuleReference`, отказы

- **`Trees.encloses`** — javadoc обещал «позиция на границах узла», а конец не включается (как у
  `Ranges.containsPosition(Range, …)`). Поведение верное и нужное `findInnermostEnclosing`;
  исправлено описание, а поведение закреплено характеризующим тестом
  `TreesTest.enclosesIncludesStartAndExcludesEnd` (прямых тестов у метода не было).
- **`ModuleReference.extractMethodNameFromModifier`** повторял цепочку `accessCallMethodName` того
  же класса — теперь вызывает её. Прочие null-безопасные цепочки «вызов → имя метода» в
  провайдерах заканчиваются разным (текст, токен, диапазон) — трёхстрочные, оставлены.
- **Отклонены с обоснованием** (бэклог): аннотации конструктора `.os` тремя способами (разные
  блокировки и круг файлов — намеренно), сборка refactor-`CodeAction` (отличия видимы клиенту).
- **Итог сканирования после раунда 69:** четыре существенных находки закрыты (71–75), две
  отклонены, мелочь закрыта; в `inlayhints/`, `rename/`, `aop/`, `websocket/` дублей не найдено.
- **Проверка.** `TreesTest`, `*ModuleReference*`, `*CommonModule*`, `references` (81 класс,
  341 тест) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Контрольная точка: `./gradlew check javadoc` после раунда 76

`BUILD SUCCESSFUL in 12m 14s`: 858 тестовых классов, 5987 тестов, падений нет.

## Раунд 77. Специализация дженерика семейства — по разу, а не по разу на дженерик

Источник — второе, углублённое сканирование `types/` (после раунда 76).

- **Дефект.** `ConfigurationTypesProvider.registerFamilySpecializations` обходил дженерики
  семейства и на каждый одно-параметрический звал `ConfigurationGenericExpander`, который сам
  обходит те же дженерики. При k дженериках с общим параметром (у справочника — ссылка, объект,
  менеджер, выборка, список…) каждая специализация регистрировалась k раз, а
  `registerSpecialization` на каждый вызов добавляет источник членов. Результат выглядел верным
  (дедуп по имени в `getMembers`), но каждая копия вычислялась при каждом пересчёте членов.
- **Сделано.** Экспандер зовётся по разу на имя параметра семейства.
- **Тесты.** `ConfigurationTypesProviderHelpersTest.tryRegister_specializesEachFamilyGenericOnce`
  (шпион реестра, два дженерика семейства) — красный (два вызова) → зелёный.
- **Проверка.** `types.registry`, `types.inferencer`, `*Completion*`, `*Hover*` (160 классов)
  зелёные.

## Раунд 78. Ссылка `См.` на объект метаданных с английским видом

Источник — то же углублённое сканирование `types/`.

- **Дефект.** `SymbolTypeIndex.resolveMetadataPath` собирал имя типа из вида, как он записан в
  ссылке, и русского суффикса: `См. Catalog.Товары.Товары` давало `CatalogТабличнаяЧасть.…` и
  `CatalogОбъект.…` — таких имён не регистрирует никто (английские алиасы —
  `CatalogTabularSection.…`/`CatalogObject.…`), и параметр оставался без типа. Русская запись
  того же пути работала.
- **Сделано.** Вид нормализуется к русскому через `MDOType.fromValue` — тот же разбор вида, что
  у запросов и диагностик; неизвестное слово остаётся как есть.
- **Тесты.** `MetadataSeeRefInferenceTest.seeRefWithEnglishKindResolvesLikeRussianOne` (табличная
  часть и реквизит объекта через `Catalog.`) — красный (пусто) → зелёный; фикстура
  `MetadataSeeRef.bsl` дополнена методом с английскими ссылками.
- **Проверка.** Все тесты `types` (304 класса, 2477 тестов) зелёные; `javadoc`, `spotlessCheck`
  зелёные.

## Раунд 79. Вложенные соединение и ВЫБОР в подсветке запроса — закреплено тестами

Источник — сканирование `documenthighlight/` и др. после раунда 76: утверждалось, что подсветка
берёт внешнее соединение вместо вложенного в подзапрос и внешний `ВЫБОР` вместо вложенного в его
операнд (ручной поиск узла вместо `Trees.findInnermostEnclosing`).

- **Проверено — дефекта нет.** Тесты на оба случая зелёные на текущем коде: обход
  `Trees.findAllRuleNodes` отдаёт внутренний узел раньше внешнего, поэтому первый подходящий и есть
  самый вложенный. Ручной поиск оставлен — он не расходится с каноническим по результату.
- **Тесты оставлены как сторожа**: `SDBLJoinDocumentHighlightSupplierTest.joinInsideSubqueryHighlightsItselfNotTheEnclosingJoin`,
  `SDBLCaseDocumentHighlightSupplierTest.caseNestedInOperandHighlightsItselfNotTheEnclosingCase` —
  поведение на вложенности до сих пор не было закреплено ничем.

## Раунд 80. Свёртка рангов автодополнения — `CompletionPriority.Fold`

Источник — бэклог, пункт 8 углублённого сканирования.

- **Проблема.** Свёртка «есть доступная альтернатива / есть недоступная → ранг» была записана
  четырежды (`CompletionContextEvaluation` ×3, `CompletionMethodEvaluation`), а готовый для неё
  `CompletionPriority.combine(int[])` не вызывался нигде: массив не подходит циклам с досрочным
  выходом (`rankSharedReceiverUnion` возвращает `null` посреди обхода).
- **Сделано.** Накопитель `CompletionPriority.Fold` (`add`/`result`); четыре копии сведены к нему,
  мёртвый `combine(int[])` удалён, `combine(boolean, boolean)` стал приватным.
- **Тесты.** Новый `CompletionPriorityTest` — шесть из девяти случаев красные на заглушке
  (три «всё доступно» совпадают с ответом заглушки) → зелёные.
- **Проверка.** `completion`, `*Completion*` (32 класса, 372 теста) зелёные; `javadoc`,
  `spotlessCheck` зелёные.

## Раунд 81. Сборка `DiagnosticInfo` — одним рецептом фабрики

Источник — тот же пункт 8.

- **Проблема.** «Классы диагностик → `new DiagnosticInfo(класс, конфигурация, интернер)`» — четыре
  копии: три метода `DiagnosticInfosFactory` и `DiagnosticInfos.init`.
- **Сделано.** `init` и обе карты фабрики строятся из `createDiagnosticInfos`; `DiagnosticInfos`
  больше не держит интернер. Обе карты `DiagnosticInfos` по-прежнему над одним списком
  экземпляров — `refresh()` обходит только `byCode` (отмечено комментарием).
- **Не сделано (решение):** удаление не вызываемых в коде `createDiagnosticInfosByCode`/`ByClass` —
  фабрика публична через `BSLLSBinding`, это внешний контракт.
- **Проверка.** Поведение не меняется, новых тестов нет; `diagnostics.infrastructure`,
  `BSLLSBindingTest`, `*DiagnosticInfo*`, `*DiagnosticProvider*` и др. (10 классов, 51 тест)
  зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 82. Обходы канонических помощников в `types/`

Источник — пункт 7 углублённого сканирования `types/`.

- `TableCollectionInference.resolveMember` повторял `TypeRegistry.findMember` (тот же поиск через
  `MemberLookupSession`) по каждому типу получателя — теперь зовёт его. Модульный тест
  `ReturnBodyMemberLookupReuseTest` мокал реестр и стаб `getMembers` — мок теперь вызывает
  настоящий `findMember` (`thenCallRealMethod`), счётчик просмотров проверяет то же.
- `SymbolTypeIndex.findLocalFunction` — линейный проход методов вместо
  `SymbolTree.getMethodSymbol(name).filter(isFunction)`.
- `ConfigurationModuleMembersProvider.register` собирал русское имя self-типа модуля сам, мимо
  `selfTypeQualifiedName`, по которому self-тип ищет резолвер обработчиков (раунд 66), — теперь
  имя одно; английский алиас по-прежнему строится рядом.
- **Проверка.** Все `types`, `*EventHandler*`, `*Hover*`, `*Completion*` (347 классов) зелёные
  после правки мока; `javadoc`, `spotlessCheck` зелёные.

## Раунд 83. Имя ссылочного типа владельца — `FormPlatformTypes.typeNameWithSuffix`

`CatalogOwnerTypesRegistrar.refTypeName` побуквенно повторял `FormPlatformTypes.typeNameWithSuffix`
(«`Справочник.Х` → `СправочникСсылка.Х`») — удалён. Общий помощник для остальных мест, где имя
типа склеивается из вида, суффикса и имени, отклонён (бэклог, пункт 4). Тесты `types.registry`
(78 классов) зелёные.

## Раунд 84. Типизированная коллекция — `CollectionReturnsSpecializer.registerCollection`

Источник — пункт 1 углублённого сканирования `types/`.

- **Проблема.** Три шага «тип элемента — своя строка → наследование свойств обхода от обобщённой
  коллекции → уточнение членов под строку» повторялись в пяти местах (набор записей, табличная
  часть объекта, три вида коллекций данных формы), и в трёх из них — с одинаковым
  предупреждением: порядок первых двух шагов обязателен, иначе обход теряет колонки.
- **Сделано.** `registerCollection` выполняет шаги в нужном порядке; копии и приватная обёртка
  `FormDataTypesRegistrar.specializeCollectionReturns` удалены. Строки набора записей в данных
  формы оставлены на `register`: наследование свойств там делает вызывающий для всех видов
  данных формы (отмечено комментарием) — порядок при этом верный.
- **Тесты.** `CollectionReturnsSpecializerTest.registeredTypedCollectionIteratesOverItsOwnRow`
  красный на заглушке → зелёный.
- **Проверка.** Все `types`, `*Hover*`, `*Completion*` (339 классов, 2912 тестов) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 85. Конфигурационный тип с английским именем — `TypeRegistry.registerConfigurationType(ru, en)`

Источник — пункт 3 углублённого сканирования `types/`.

- **Проблема.** «Завести тип по русскому имени и, если английское есть и отличается, — алиас»
  было записано восемь раз (`FormTypeFactory`, `ConfigurationTypesProvider` ×6 с приватным
  `registerWithAlias`, `ConfigurationModuleMembersProvider`, `XdtoTypesProvider`) с разными
  проверками: где-то на `null`, где-то на пустоту, где-то ни на что.
- **Сделано.** Перегрузка `registerConfigurationType(ru, en)` с одной проверкой (`null`, пустое,
  совпадающее — без алиаса); приватный `registerWithAlias` удалён. Отображаемое имя по-прежнему
  задаёт вызывающий — у разных видов типов оно своё (у XDTO и форм — полные имена, у
  объектных типов — от специализации), различие осознанное. Подозрение сканирования на `XdtoTypesProvider`
  (нет проверки пустого английского имени) не подтвердилось: префиксы языков разные, имя никогда
  не пустое и не совпадает.
- **Тесты.** `TypeRegistryRegistrationTest.registerConfigurationTypeWithEnglishNameResolvesByBothNames`
  красный на заглушке → зелёный.
- **Проверка.** Все `types`, `*Hover*`, `*Completion*`, `*EventHandler*` (347 классов, 2955 тестов)
  зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 86. Параметр с другими типами — `ParameterDescriptor.withTypes`

Источник — пункт 6 углублённого сканирования `types/`.

- **Проблема.** Четыре места (`MemberDescriptor.specialize`, `PlaceholderBinder`,
  `CollectionReturnsSpecializer`, `StructureParameterFieldsRegistrar`) пересобирали параметр
  шестиаргументным конструктором, переписывая все поля ради одного — типов; пропуск любого поля
  (например, `variadic`) терял бы его молча.
- **Сделано.** `withTypes` рядом с уже существовавшим `withVariadic`; четыре места сведены к нему.
  Аналог для сигнатур (`withReturnTypes`) и обход `MdoMemberFactory.property` — оставлены в
  бэклоге: места различаются (часть сбрасывает `generic`, часть меняет и параметры).
- **Тесты.** `ParameterDescriptorTest.withTypesKeepsEverythingButTypes` красный на заглушке →
  зелёный.
- **Проверка.** Все `types`, `*Hover*`, `*Completion*`, `*SignatureHelp*` (343 класса, 2972 теста)
  зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 87. Подсветка парного ключевого слова при курсоре сразу за ним

Источник — сканирование `documenthighlight/` после раунда 76 (пункт 5).

- **Дефект.** `DocumentHighlightProvider` находит токен и при курсоре сразу за ним (повтор на
  символ левее), но поставщики подсветки метода и области искали свою конструкцию по исходной
  позиции курсора. За `КонецПроцедуры` / `#КонецОбласти` курсор стоит уже за концом диапазона
  (конец исключён) — и подсветки не было, хотя у `Процедура`/`#Область` в той же позиции
  (сразу за словом) она есть.
- **Сделано.** `SubroutineDocumentHighlightSupplier` и `RegionDocumentHighlightSupplier` ищут
  конструкцию по началу найденного токена; у области, если токена не дали, — по курсору, как
  раньше.
- **Тесты.** `SubroutineDocumentHighlightSupplierTest.endProcedureKeywordWithCursorRightAfterItHighlightsTheProcedure`,
  `RegionDocumentHighlightSupplierTest.regionEndWithCursorRightAfterItHighlightsTheRegion` — красные
  (пусто) → зелёные.
- **Проверка.** `documenthighlight`, `*DocumentHighlight*` (13 классов, 77 тестов) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 88. Подсветка `ВЫБОР … КОНЕЦ` в запросе при курсоре сразу за `КОНЕЦ`

Тот же дефект, что в раунде 87, в подсветке языка запросов: токен находился и при курсоре
сразу за `КОНЕЦ`, а выражение `ВЫБОР` искалось по курсору — за концом выражения. Теперь — по
началу найденного токена. Соединение не тронуто: его диапазон тянется за `ПО` до конца условия,
и курсор за ключевым словом соединения из него не выходит — красного теста для него нет.
`SDBLCaseDocumentHighlightSupplierTest.endKeywordWithCursorRightAfterItHighlightsTheCase` красный
(пусто) → зелёный; тесты подсветки (13 классов) зелёные.

## Контрольная точка: `./gradlew check javadoc` после раунда 88

`BUILD SUCCESSFUL in 11m`: 859 тестовых классов, 6006 тестов, падений нет.

## Раунд 89. Сторож канонических реализаций — `CanonicalImplementationsTest`

- **Проблема.** Дубли, убранные в раундах 66–86, ничем не закреплены: следующая копия рецепта
  прошла бы сборку молча (раунд 66 сам нашёл копии, появившиеся мимо уже существовавшего
  помощника).
- **Сделано.** Параметризованный тест по исходникам: девять образцов скопированной записи — каждый
  со своим каноническим владельцем (`TypeRef.UNDEFINED`, `MemberDescriptor.returnType()`,
  `MethodSymbol.getParameterDefinition`, `TypeRegistry.findSpecialization`,
  `registerConfigurationType(ru, en)`, сборка `DiagnosticInfo`, `ReportFile.commit(name)`,
  `CompletionPriority.Fold`, `CollectionReturnsSpecializer.registerCollection`). Образцы узкие —
  ровно та запись, что копировалась: при подборе выяснилось, что «первый тип набора» в загрузчиках
  метаданных и «тип элемента + наследование» без уточнения членов — другая семантика, их образец
  не ловит.
- **Проверка сторожа.** Каждый образец проверен на версии файла до соответствующего раунда
  (`git show <коммит>~1:…`) — все девять находят исходную копию; на текущем коде — 9 из 9 зелёные.
- **Ацикличность и границы** (критерии приёмки) уже машинно проверяются: `ArchitectureTest.layer_dependencies_are_respected`
  (карта слоёв с белыми списками, без обратных рёбер) и `acyclic_domains_stay_free_of_cycles`;
  полный `check` после раунда 88 их прогнал.

## Раунд 90. Подсветка: поиск токена и ближайшего цикла — через `Trees`

Источник — бэклог, пункт 9 (остаток по `documenthighlight/`).

- `DocumentHighlightProvider` (и его копия в тестовом `DocumentHighlightTestUtils`) вручную
  повторял «токен у курсора, иначе — на символ левее»; второй шаг — это ровно
  `Trees.findTerminalNodeEndingAtPosition` (токен, не содержащий курсор, но содержащий символ
  левее, кончается у курсора). Оба сведены к `findTerminalNodeContainsPosition(…).or(…EndingAt…)`.
- `LoopStatementDocumentHighlightSupplier.findNearestLoopStatement` — ручной подъём «сам узел или
  ближайший предок с правилом из набора»; у `Trees` был такой вариант для одного правила и подъём
  по набору без самого узла. Добавлен `Trees.getAncestorOrSelfByRuleIndex(узел, набор)` —
  парный к одиночному; ручной обход удалён. Прочие ручные подъёмы (`AbstractMagicValueDiagnostic`,
  `FlowLayout`) — с ограничением глубины или другим условием, не копии.
- **Отклонено:** `addTokenHighlight` в двух базовых классах (BSL и запросы) — четырёхстрочные,
  разные иерархии; общий помощник — формальное единообразие.
- **Тесты.** `TreesTest.ancestorOrSelfByAnyRuleStartsFromTheNodeItself` красный на заглушке →
  зелёный; поведение провайдера закрыто тестами «курсор сразу за токеном» (раунды 87–88).
- **Проверка.** `TreesTest`, `documenthighlight`, `ArchitectureTest`, `CanonicalImplementationsTest`
  (16 классов, 120 тестов) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 91. Дочерние объекты формы как свойства — `FormPlatformTypes.namedProperties`

Источник — пункт 5 углублённого сканирования `types/`.

- **Проблема.** Реквизиты (`FormDataTypesRegistrar.buildAttributeMembers`), элементы
  (`FormItemTypesRegistrar.buildItemMembers`) и команды формы (`FormParametersRegistrar.buildCommandMembers`)
  становились свойствами по одному правилу — пропустить безымянные, из одноимённых без учёта
  регистра оставить первый, имя одно в обеих локалях, описание — заголовок, — записанному трижды.
  Разойдись правило (например, дедупликация) в одном месте — `Элементы.Х` и `Команды.Х` вели бы
  себя по-разному.
- **Сделано.** `namedProperties(дети, имя, заголовок, типы)`; три построителя — однострочные вызовы.
  Типы считаются для каждого именованного объекта до дедупликации — как и прежде. Параметры формы
  (`buildParameterMembers`) не тронуты: без дедупликации и без заголовка — другое правило.
  Сторож `CanonicalImplementationsTest` дополнен образцом цепочки «нейтральное имя + заголовок»
  (проверен на версии до правки).
- **Тесты.** `FormPlatformTypesNamedPropertiesTest` красный на заглушке → зелёный.
- **Проверка.** Все `types`, `*Hover*`, `*Completion*`, `*Form*` (370 классов, 3016 тестов) зелёные;
  `javadoc`, `spotlessCheck` зелёные.

## Раунд 92. Сигнатура с другими типами возврата — `SignatureDescriptor.withReturnTypes`

Источник — пункт 6 углублённого сканирования `types/` (вторая половина; первая — раунд 86).

- **Сделано.** `withReturnTypes` рядом с `withMetadata`; три пересборки сигнатуры
  четырёхаргументным конструктором ради одного поля (`MetadataCollectionSpecializer` ×2,
  `RegisterFamilies`) сведены к нему. `CollectionReturnsSpecializer.refine` меняет и параметры, и
  возврат — оставлен конструктором; сигнатура конструктора `.os`-класса собирается заново из
  строкового описания — тоже.
- **Отклонено:** замена девятиаргументных `new MemberDescriptor(…)` в `MetadataCollectionSpecializer`
  на `MdoMemberFactory.property` — фабрика строит описание из пустой строки, а не из
  `BilingualString.EMPTY`; поведение не доказуемо то же, а выигрыш — два вызова.
- **Тесты.** `SignatureDescriptorTest.withReturnTypesKeepsEverythingButReturnTypes` красный на
  заглушке → зелёный.
- **Проверка.** Все `types`, `*Hover*`, `*Completion*`, `*SignatureHelp*` (344 класса, 2974 теста)
  зелёные; `javadoc`, `spotlessCheck` зелёные.
- **Сторож.** `CanonicalImplementationsTest` дополнен образцами полной пересборки параметра и
  сигнатуры ради одного поля (12 случаев); оба проверены на версиях файлов до правок.

## Раунд 93. Ревизия открытых пунктов бэклога

Каждый открытый пункт без пометки «решение» перепроверен по коду.

- **Устарели (уже сделано раньше, запись не обновили):** мёртвые настройки журнала и выбор
  исполняемого файла раннера тестов (раунд 55), обход `BuiltinResources.open()` (раунд 61) —
  записи исправлены.
- **Проверено, дефекта нет:** неохраняемые `Path.of(uri)` получают только `file`-URI (фильтр
  рабочих папок по схеме при `initialize`, применимость линз тестов по `file`-каталогу,
  CLI-отчёт над файлами на диске).
- **Оставлено с обоснованием:** различие реакции загрузчиков встроенных ресурсов на битый ресурс
  (быстрый отказ для ошибки сборки оправдан — решение мейнтейнера), пустые наборы в
  `MethodReturnTypeIndexer.dependentsByUri` (ограничены числом документов).
- **Итог.** Открытыми остаются только пункты, ждущие решения мейнтейнера (продуктовые, ломающие
  формат или контракт) и рассмотренные-отклонённые с обоснованием. Все пакеты прошли
  сканирование дублей, `types/` — дважды; находки сканирований закрыты или отклонены по месту.


## Контрольная точка: `./gradlew check javadoc` после раунда 93

`BUILD SUCCESSFUL in 12m 2s`: 861 тестовый класс, 6021 тест, падений нет.

## Раунд 94. Проверка критериев приёмки: отвязка ядра от транспорта и описание пакетов

- **Отвязка бизнес-логики от фреймворка — проверена и закреплена.** Ядро анализа (`cfg`, `context`,
  `diagnostics`, `index`, `references`, `types`, `utils`) пользуется из LSP4J только словарём
  данных (`Position`, `Range`, `Diagnostic`, `SymbolKind`, `Either`) — общей системой координат
  подсистем; сервисов LSP4J (`LanguageClient`, запуск), веб/servlet-стека, MCP и Spring AI в нём
  нет ни одного импорта. Из Spring — только контейнер (компоненты, события, кеш). Хранилища
  данных у сервера нет (состояние — в памяти и кеше EhCache за `CacheConfiguration`). Правило
  `ArchitectureTest.analysis_core_is_transport_agnostic` (не пустое: ArchUnit падает, если под
  `that()` не попало ни одного класса).
- **Ответственность модулей записана у каждого пакета.** Все top-level пакеты описаны
  `package-info.java` и/или `CLAUDE.md`; без описания были два подпакета — `types/references` и
  `mcp/dto`, описания добавлены. Новый сторож `PackageDocumentationTest` — красный (ровно эти
  два) → зелёный.
- **Охват областей обзора** (по журналу):

  | Область | Раунды |
  |---|---|
  | UI / дизайн-система | нет UI; слой представления — провайдеры LSP: 31–33, 41–47, 56–57, 87–90 |
  | Бизнес-логика и модель | диагностики 34–40; система типов 51–54, 65–86, 91–92; контекст 36, 46, 67 |
  | API и контракты данных | 61–64 (решения — в бэклоге: форматы отчётов, MCP, CLI) |
  | Состояние и поток данных | 49, 51–54, 59, 65 |
  | Доступ к данным и кеш | 48–49, 51–56, 61 |
  | Конфигурация и env | 49, 55, 58, 64 |
  | Ошибки и повторы | 49–50, 57, 62 |
  | Журнал и наблюдаемость | 49–50, 55; Sentry — решение |
  | Права и аутентификация | 49 (сетевые режимы без аутентификации — решение мейнтейнера) |
  | Имена, каталоги, границы | ArchUnit (именование, карта слоёв), 89, 94 |
  | Утилиты и общая инфраструктура | 40–47, 76, 90 |
  | Тесты, сборка, инструменты | 38, 60, 64, 89, 94; javadoc вне `check` — отдельный workflow |
- **Проверка.** `ArchitectureTest` (23 правила), `CanonicalImplementationsTest` (12),
  `PackageDocumentationTest` зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 95. Полнота карты слоёв — проверка, а не соглашение

- **Проверено.** Карта слоёв `layer_dependencies_are_respected` объявляет слоем каждый top-level
  пакет, кроме `aop` (рёбра AspectJ-weaving), — сверено со списком каталогов. Карта — белый
  список без обратных рёбер, поэтому направление зависимостей и ацикличность закреплены для всего
  проекта, а не только для пакетов правила `acyclic_domains_stay_free_of_cycles`.
- **Дыра.** Полнота держалась на соглашении: новый top-level пакет вне карты под правило не
  попадал. Добавлено `ensureAllClassesAreContainedInArchitectureIgnoring(aop)`.
- **Проверка сторожа мутацией.** Временный класс в новом пакете `zzprobe` валит
  `layer_dependencies_are_respected` с указанием `zzprobe.Probe`; класс удалён, правило зелёное.

## Контрольная точка: `./gradlew check javadoc` после раунда 95

`BUILD SUCCESSFUL in 11m 23s`: 862 тестовых класса, 6023 теста, падений нет — включая новые
правила `analysis_core_is_transport_agnostic`, полноту карты слоёв и `PackageDocumentationTest`.
В бэклог внесено решение о сужении публичного интерфейса пакетов (внешний контракт библиотеки).

## Раунд 96. Замер публичного интерфейса пакетов, права доступа и данные

- **Публичный интерфейс** проверен систематически: разовым замером ArchUnit (тест удалён после
  прогона) по каждому top-level пакету посчитаны публичные классы и те из них, на которые нет
  ни одной ссылки извне пакета — 486 из 861. Разбор и вывод — в `map.md`: основная доля —
  бины-расширения, находимые Spring по аннотации (диагностики 217 из 240, поставщики фич); их
  публичность — модель расширения и внешний контракт. У пакетов-фундаментов (`utils`,
  `configuration`, `providers`, `completion`, `events`, `client`) публичная поверхность
  действительно используется извне. Сужение видимости — решение мейнтейнера (бэклог).
- **Права доступа и доступ к данным** — сведены в `map.md`: аутентификации нет (сетевые режимы —
  решение), хранилища нет; чтение документа и файла конфигурации — по одному месту, второе
  закреплено ArchUnit.

## Раунд 97. Решения мейнтейнера по отложенным пунктам

- **Загрузчики встроенных ресурсов — «писать в журнал и продолжать везде».** Пять загрузчиков
  (`GlobalScopeProvider` ×2, `BuiltinTypesJsonLoader`, `KeywordMetadataLoader`,
  `BuiltinQueryTables`) реагировали на битый ресурс по-разному: три валили старт (ловили только
  `IOException`, а Jackson 3 бросает непроверяемое), два писали WARN. Теперь у всех
  `IOException | RuntimeException` и ERROR — битый JSON и данные не той формы одинаково дают
  пустые сведения и запись в журнале. `BuiltinQueryTables.read` получил путь параметром для теста.
  Новый `BuiltinResourceLoadersTest` (битый JSON и неверная форма для каждого загрузчика) —
  7 случаев красные → зелёные.
- **`DiagnosticInfosFactory.createDiagnosticInfosByCode`/`ByClass` — оставить публичным API.**
- **Тип возврата из описания и определяемый тип в `ОписаниеТипов`** — вынесены на обсуждение:
  ишью #5028 и #5029.
- **Проверка.** `BuiltinResourceLoadersTest`, `types.registry`, `*KeywordMetadata*`,
  `*GlobalScope*` (82 класса, 755 тестов) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Контрольная точка: `./gradlew check javadoc` после раунда 97

`BUILD SUCCESSFUL in 13m 43s`: 863 тестовых класса, 6030 тестов, падений нет.

## Раунд 98. Кеш плейсхолдеров `TypeRef` — только для имён с плейсхолдерами

Решения мейнтейнера по отложенному: публичный интерфейс пакетов не сужать, PR не разбивать,
ишью #5028 и #5029 — отложены (записано в бэклог).

- **Дефект (память).** `TypeRef.placeholders()` клал в статический пожизненный кеш результат для
  каждого имени типа, у которого его спрашивали, — в том числе для тысяч типов конфигурации без
  единого плейсхолдера; javadoc при этом обещал «сотни» записей. Кеш копился по всем открытым
  рабочим областям до конца жизни процесса.
- **Сделано.** Имя без `<` плейсхолдеров не содержит (так их ищет и разбор `ContextNames`) — для
  него сразу пустой список без записи в кеш; javadoc приведён к факту.
- **Тесты.** `TypeRefSpecializeTest.namesWithoutPlaceholdersDoNotGrowTheCache` — красный
  (100 имён конфигурации дали 100 записей) → зелёный.
- **Проверено, не дефект:** разные уровни журнала в `ExpressionTypeInferencer` — ERROR со стеком у
  сбоя запрошенного расчёта (дефект вывода), DEBUG — у необязательного упреждающего расчёта ячеек
  и у документа, выгруженного во время вывода.
- **Проверка.** `types.model`, `types.registry` (96 классов, 906 тестов) зелёные; `javadoc`,
  `spotlessCheck` зелёные.

## Раунд 99. Значения по умолчанию сетевых опций — один источник

- **Проверено (гипотеза бэклога).** `--server.port` и `--app.websocket.lsp-path` названы ключами
  свойств Spring: значение Spring берёт прямо из аргументов, по умолчанию — из
  `application.properties`. Поля picocli нужны только справке и разбору опций. Значение по
  умолчанию было записано трижды: в свойствах, в `defaultValue` и текстом в описании.
- **Сделано.** Описания опций берут значение из `${DEFAULT-VALUE}` (справка побуквенно та же —
  проверено на собранном jar); новый `CommandOptionDefaultsTest` сверяет `defaultValue` каждой
  опции, названной ключом свойства, с `application.properties` (непустой: проверяет обе опции).
  У `--mcp-path` значение по умолчанию — эндпоинт Spring AI, ключа в свойствах нет, сверять не с чем.
- **Проверка.** `cli`, `*MainApplication*` (11 классов, 61 тест) зелёные; `javadoc`,
  `spotlessCheck` зелёные.

## Раунд 100. Карточка глобального свойства в MCP — через `TypeMemberDto.from`

Источник — углублённое сканирование `semantictokens/`, `hover/`, `mcp/`, `configuration/`.

- **Дефект.** `global_member_info` собирал карточку глобального свойства и перечисления вручную:
  описание — всегда русское (`description()` вместо `displayDescription(language)`), метаданные
  синтакс-помощника обнулялись (хотя описание инструмента их обещает), из объединения типов значения
  оставался первый. Функции шли через `TypeMemberDto.from` — как и тот же член в
  `global_member_search`.
- **Сделано.** Карточка свойства — `TypeMemberDto.from(member, language)`; ручная сборка удалена.
- **Тесты.** Новый `GlobalMemberInfoToolTest` (свойство с переводом, метаданными и двумя типами)
  красный → зелёный; `McpToolsTest.globalMemberInfoDescribesValuesLikeGlobalMemberSearch` сверяет
  оба инструмента на реальных членах глобального контекста.
- **Проверено, не дефект:** «необъявленный `IOException` из `Absolute.path` прерывает синхронизацию
  MCP roots» — битый путь отсекает раньше `McpWorkspaceFolders.toWorkspaceFolderUri`, который сам
  ловит `Exception`; сторож `McpRootsChangeConsumerTest.rootWhosePathCannotBeCanonicalizedIsSkippedWithoutLosingTheOthers`
  (цикл символических ссылок; только Linux/macOS).
- **Проверка.** `mcp` (18 классов, 190 тестов) зелёные; `javadoc`, `spotlessCheck` зелёные.

## Раунд 101. `CommitTransactionOutsideTryCatch` — по имени метода, а не по тексту оператора

Источник — углублённое сканирование `diagnostics/`.

- **Дефект (ложное срабатывание).** Фиксацией транзакции считался любой вызов глобального метода,
  в тексте которого встречается `ЗафиксироватьТранзакцию` (`find()` по `ctx.getText()`): например,
  `Сообщить("ЗафиксироватьТранзакцию не выполнена")`. Парная `BeginTransactionBeforeTryCatch`
  сравнивает имя вызываемого метода целиком (`matches()`).
- **Сделано.** Тот же разбор, что у парной диагностики: имя метода из `callStatement → globalMethodCall`.
- **Тесты.** `CommitTransactionOutsideTryCatchDiagnosticTest.methodNameMentionedInArgumentIsNotACommit`
  красный → зелёный; прежние 8 срабатываний на основной фикстуре сохранились.

## Раунд 102. `IncorrectUseOfStrTemplate` — шаблон из ближайшего присваивания

Источник — углублённое сканирование `diagnostics/`.

- **Дефект (ложное срабатывание).** Поиск значения шаблона шёл назад по операторам до первого
  присваивания переменной **константой**: присваивание неконстанты (`Шаблон = ПолучитьШаблон()`)
  пропускалось, и проверялась более ранняя, уже перезаписанная константа.
- **Сделано.** Поиск останавливается на ближайшем присваивании переменной; не константа —
  шаблон статически неизвестен, проверки нет. Присваивания другим переменным по-прежнему пропускаются.
- **Тесты.** `IncorrectUseOfStrTemplateDiagnosticTest.overwrittenConstantTemplateIsNotChecked`
  красный → зелёный; прежние срабатывания основной фикстуры сохранились.

## Раунд 103. `UsingObjectNotAvailableUnix` — и конструктор с именем типа в аргументе

Источник — углублённое сканирование `diagnostics/`.

- **Дефект (пропуск).** Диагностика брала имя типа только из `Новый COMОбъект(…)` и пропускала
  форму `Новый("COMОбъект", …)`: при пустом `typeName()` выходила сразу. Каноническое
  `Constructors.typeName` разбирает обе формы, им пользуются `FileSystemAccess`, `InternetAccess`
  и др.
- **Сделано.** Имя типа — `Constructors.typeName`.
- **Тесты.** `UsingObjectNotAvailableUnixDiagnosticTest.constructorWithTypeNameAsArgumentIsChecked`
  красный → зелёный; прежние три срабатывания сохранились.

## Раунд 104. Hover конструктора класса OneScript — ключевое слово на языке интерфейса

Источник — углублённое сканирование `hover/`.

- **Дефект.** `OScriptClassConstructorRenderer` писал заголовок `Новый Класс(…)` всегда по-русски,
  тогда как hover конструкторов платформенных типов (`ConstructorHoverBuilder`) берёт ключевое
  слово на языке интерфейса.
- **Сделано.** Ключевое слово — `Keywords.NEW` по языку конфигурации (канонический двуязычный
  словарь ключевых слов, им же пользуются форматтер и автодополнение).
- **Тесты.** `HoverProviderOScriptLibraryTest.constructorHoverOfOScriptClassUsesTheKeywordOfTheInterfaceLanguage`
  красный → зелёный.
- **Проверка.** `hover`, `*Hover*`, `ArchitectureTest` (25 классов, 194 теста) зелёные; `javadoc`,
  `spotlessCheck` зелёные.
