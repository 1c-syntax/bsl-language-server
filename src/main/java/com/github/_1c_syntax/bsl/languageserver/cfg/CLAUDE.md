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

## Потребители

`UnreachableCodeDiagnostic` (мёртвый код), `AllFunctionPathMustHaveReturnDiagnostic`
(возврат на всех путях). Диагностика, которой нужен анализ путей, строит CFG методом
`CfgBuildingParseTreeVisitor.buildGraph(...)` по телу метода.

## Правки в этом каталоге

- Новый вид управляющей конструкции = новая `CfgVertex`-вершина + ветка в
  `CfgBuildingParseTreeVisitor`; при добавлении типа ребра расширь `CfgEdgeType` и оба обходчика.
- Граф — на JGraphT; используй его API, не изобретай обход с нуля.
