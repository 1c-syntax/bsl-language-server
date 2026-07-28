<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# cfg/ — граф потока управления (Control Flow Graph)

Построение и обход CFG кода BSL. На нём строятся диагностики анализа путей (недостижимый код,
обязательный возврат). См. корневой [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Модель графа

- **`ControlFlowGraph`** — направленный граф на JGraphT (`DefaultDirectedGraph<CfgVertex, CfgEdge>`)
  с точкой входа и единственной вершиной выхода.
- **`CfgVertex`** (абстракт.) — вершина, опц. привязана к AST-узлу (`ParserRuleContext`). Виды:
  `BasicBlockVertex` (последовательные операторы), `ConditionalVertex` (Если/ИначеЕсли),
  `BranchingVertex` (абстр. ветвление), `LoopVertex` → `For/While/ForeachLoopVertex`,
  `TryExceptVertex`, `LabelVertex`, `ExitVertex` (терминальная),
  `PreprocessorConditionVertex` (`#Если`).
- **`CfgEdge`** / **`CfgEdgeType`**: `DIRECT`, `TRUE_BRANCH`, `FALSE_BRANCH`, `LOOP_ITERATION`,
  `ADJACENT_CODE`.

## Построение и обход

- **`CfgBuildingParseTreeVisitor`** (`BSLParserBaseVisitor`) — строит граф: `buildGraph(CodeBlockContext)`
  → `ControlFlowGraph`. Конфигурируется: `produceLoopIterations()`,
  `producePreprocessorConditions()`, `determineAdjacentDeadCode()`. Внутренний помощник —
  `StatementsBlockWriter` (накопление операторов, разрезание блоков, контексты переходов
  return/break/continue/исключение).
- Обход: **`ControlFlowGraphWalker`** (ручной: `start()`, `walkNext([CfgEdgeType])`,
  `availableRoutes()`) и **`AbstractCfgVisitor`** (DFS-визитор с диспетчеризацией по типам вершин
  и рёбер: `visitBasicBlock`, `visitConditionalVertex`, `visitTrueEdge`, …).

## Настройки построения и кэш

- **`CfgBuildOptions`** — неизменяемый набор настроек (`loopIterations`,
  `preprocessorConditions`, `adjacentDeadCode`) плюс `buildGraph(codeBlock)`. Все три
  настройки передаются построителю явно, поэтому набор задаёт структуру графа целиком.
- Готовый граф берётся **не построением на месте**, а из
  `index/ControlFlowGraphIndex.graphOf(documentContext, codeBlock, options)`: он строит
  граф лениво, делит его между потребителями и сбрасывает по жизненному циклу документа.
  Выдаваемый экземпляр общий — **только для чтения**.

## Потребители

`UnreachableCodeDiagnostic` (мёртвый код), `AllFunctionPathMustHaveReturnDiagnostic`
(возврат на всех путях).

## Правки в этом каталоге

- Новый вид управляющей конструкции = новая `CfgVertex`-вершина + ветка в
  `CfgBuildingParseTreeVisitor`; при добавлении типа ребра расширь `CfgEdgeType` и оба обходчика.
- Новая настройка построения = поле в `CfgBuildOptions` (она же — часть ключа кэша), а не
  очередной сеттер, выставляемый потребителем самостоятельно.
- Пакет — лист в карте слоёв (`ArchitectureTest`): он ничего не знает про документы, контекст
  и Spring. Всё, что требует этих знаний, живёт в `index/`.
- Граф — на JGraphT; используй его API, не изобретай обход с нуля.
