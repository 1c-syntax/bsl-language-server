<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# utils/ — общие утилиты

Вспомогательные хелперы для AST, диапазонов, путей, языка 1С. **Перед тем как писать свой хелпер —
проверь, нет ли готового здесь** (особенно при разработке диагностик/провайдеров). См. корневой
[CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Что чаще всего переиспользуют

- **AST / парсинг:** `Trees` (навигация по ANTLR-дереву: поиск узлов/предков/потомков, токены,
  позиции); `DiagnosticHelper` (сравнение узлов, хелперы конфигурации).
- **Диапазоны:** `Ranges` (создание/проверка `Range` из контекстов и токенов).
- **Язык 1С:** `Keywords` (двуязычные имена ключевых слов ru/en), `Modules` (типы модулей,
  паттерн `ЭтотОбъект`/`ThisObject`), `Regions` (имена областей), `Methods` (имена методов из
  контекста вызова, конструкторы OneScript), `bsl/Constructors`.
- **Файлы/пути:** `BSLFiles` (поиск .bsl/.os с исключениями), `PathExclusionUtils` (glob-исключения).
- **Метаданные/ссылки:** `MdoRefBuilder` (строковые ссылки на объекты метаданных, интернируются),
  `ModuleReference`.
- **Диагностики:** `RelatedInformation` (`DiagnosticRelatedInformation`).
- **Прочее:** `FuzzyMatcher` (нечёткое сопоставление со скорингом — completion, поиск глобалов),
  `Resources`/`Strings` (локализованные ресурсы ru/en с интернированием), `MultilingualStringAnalyser`,
  `UTF8Control`.

## Дерево выражений — `expressiontree/`

Семантическое дерево выражения BSL (над ним работают вывод типов в [types/](../types/CLAUDE.md) и
`AbstractExpressionTreeDiagnostic`):

- **`BslExpression`** (база) + **`ExpressionNodeType`** (TERMINAL_SYMBOL, METHOD_CALL,
  CONSTRUCTOR_CALL, BINARY_OPERATION, UNARY_OPERATION, TERNARY_OPERATOR, SKIPPED_CALL_ARGUMENT,
  ERROR_EXPRESSION).
- **`ExpressionTreeBuildingVisitor`** — строит дерево из AST с учётом приоритетов операторов.
- Узлы: `BinaryOperationNode`/`UnaryOperationNode`/`TernaryOperatorNode` (+ `BslOperator` с
  приоритетами), `TerminalSymbolNode`, `MethodCallNode`/`ConstructorCallNode` (`AbstractCallNode`),
  `ErrorExpressionNode`.
- Обход — `ExpressionTreeVisitor`; сравнение деревьев — `NodeEqualityComparer` и его реализации
  (`TransitiveOperationsIgnoringComparer` и др.).

## Правки в этом каталоге

- Утилиты — без состояния (статические/чистые); не тяни сюда зависимости от Spring-контекста.
- Если хелпер нужен только одной подсистеме — держи его там, а не в общем `utils/`.
