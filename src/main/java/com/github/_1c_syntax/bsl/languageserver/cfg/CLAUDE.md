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
- **`ControlFlowGraphIndex`** (workspace-scoped) — источник готовых графов:
  `graphOf(documentContext, codeBlock, options)` строит граф лениво, делит его между
  потребителями и сбрасывает по жизненному циклу документа (база — `index/`
  `AbstractDocumentLifecycleClearableIndex`). Ключ — пара «блок кода + настройки»;
  блок сравнивается по тождественности. Выдаваемый экземпляр общий — **только для чтения**.
  Потребители берут граф отсюда, а не строят на месте.

## Потребители

`UnreachableCodeDiagnostic` (мёртвый код), `AllFunctionPathMustHaveReturnDiagnostic`
(возврат на всех путях).

## Правки в этом каталоге

- Новый вид управляющей конструкции = новая `CfgVertex`-вершина + ветка в
  `CfgBuildingParseTreeVisitor`; при добавлении типа ребра расширь `CfgEdgeType` и оба обходчика.
- Новая настройка построения = поле в `CfgBuildOptions` (она же — часть ключа кэша), а не
  очередной сеттер, выставляемый потребителем самостоятельно.
- Знание о документах живёт только в `ControlFlowGraphIndex`; само построение графа
  (`CfgBuildingParseTreeVisitor`, вершины, рёбра) про `DocumentContext` и Spring не знает.
- Граф — на JGraphT; используй его API, не изобретай обход с нуля.
