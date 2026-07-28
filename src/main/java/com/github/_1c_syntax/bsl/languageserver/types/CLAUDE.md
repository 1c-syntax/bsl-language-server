<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# types/ — система типов BSL/OneScript (v2)

Вывод и хранение типов выражений, членов платформенных/конфигурационных/пользовательских типов.
Это **актуальная** система типов (v2), полностью заменившая старый `KnownTypes`. Потребители —
hover, completion, signature help и ряд диагностик. См. корневой
[CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Фасад

- **`TypeService`** — единая точка входа. Ключевые методы: `typesAt(Reference)`,
  `expressionTypesAt(DocumentContext, Position)`, `getMembers(TypeRef, FileType[, Language])`,
  `memberAt(...)`, `receiverTypesAt(...)`, `resolve(name, FileType)`, `getConstructors(...)`,
  `definingSymbol/definingUri(...)`, `displayName(...)`. Запись `TypedMember` — найденный член.
- Вспомогательные верхнего уровня: **`DereferenceMemberMatcher`** (резолв `получатель.член`,
  объединяет члены по всем возможным типам получателя), **`MemberTypeFromCommentResolver`**
  (тип из trailing-комментария `// Тип: …`, общий для BSL и OS),
  **`PlatformMemberVersions`** (доступность/deprecation члена для целевого режима совместимости).

## Подпакеты

- **`model/`** — неизменяемая модель. Sealed-интерфейс **`Type`** (`Primitive/Platform/
  Configuration/User/Unknown/AnyType`); лёгкий ключ **`TypeRef`** (record `(TypeKind, qualifiedName)`,
  интернируется); **`TypeSet`** (неизменяемое объединение типов + `elementTypes` для коллекций +
  `localFields`); **`MemberDescriptor`** (метод/свойство: двуязычные имя/описание, сигнатуры,
  возвращаемые типы, метаданные), `SignatureDescriptor`/`ParameterDescriptor`, `BilingualString`,
  `MemberKind`, `MemberSource`, `AccessMode`.
  **`PlatformMetadata`** — «страничные» метаданные синтакс-помощника (доступность, версии
  появления/устаревания с заменами, «Возвращаемое значение», «Замечание», «Пример», «См. также»).
  Носят её три уровня: член (`MemberDescriptor.metadata`), сигнатура-конструктор
  (`SignatureDescriptor.metadata`) и сам тип (`TypeDecl.metadata` → `TypeRegistry.getTypeMetadata`
  → `TypeService.getTypeMetadata`). Потребители — hover (`PlatformMetadataRenderer`), completion
  (пометка устаревшего), диагностики `DeprecatedMethodCall`/`UnavailableMemberCall`, MCP `type_info`.
- **`registry/`** — источники и резолюция типов. **`TypeRegistry`** (workspace-scoped) —
  интернирование `TypeRef`, индекс алиасов, **мультиисточниковое** расширение членов
  (`FileType → TypeRef → List<MemberSource>`), мемоизация `getMembers()` с epoch-инвалидацией,
  синтетический `GLOBAL_CONTEXT`. **`PlatformTypesProvider`** и наследники (`Builtin…`,
  `BslContext…`, `Configuration…`, `GlobalContext…`) регистрируют платформенные/конфигурационные
  типы. **`GlobalScopeProvider`** — глобальные функции/свойства, имена классов, ключевые слова
  (JSON `builtin-globals.json`), карта `moduleTypeByUri ↔ moduleUriByType`.
  Также `MemberMetadataIndex`, `StandardAttributesResolver`.
- **`index/`** — индексы «символ → тип»: **`SymbolTypeIndex`** (возвращаемые типы методов,
  типы параметров), `InferredVariableTypeIndex` (кэш типа по символу переменной),
  `InferredExpressionTypeIndex` (кэш типа по AST-узлу выражения — не-переменные ресиверы:
  цепочки, менеджеры конфигурации, общие модули), `CallStatementByReceiverIndex`,
  `EventContractsIndex`, `WorkspaceSymbolIndex`. Оба `Inferred*`-индекса наполняет
  `ExpressionTypeInferencer` лениво; инвалидация — per-URI по жизненному циклу документа
  (общая база `index/AbstractDocumentLifecycleClearableIndex` в корне `languageserver`)
  + полный сброс на регистрацию конфигурационных типов.
- **`inferencer/`** — вывод типов. **`ExpressionTypeInferencer`** (workspace-scoped) — `infer()`
  по дереву `BslExpression` (а не сырому ANTLR); диспетчеризация по узлу (LITERAL/IDENTIFIER/CALL/
  BINARY_OP/…), защита от циклов, устойчив к битым выражениям (→ `UNKNOWN`/`TypeSet.EMPTY`).
  **`VariableFlowAnalyzer`** — тип переменной **в точке использования**: расчёт по графу потока
  управления тела (присваивание перекрывает прежний тип, в точках слияния путей — объединение).
  Присваивание задаёт тип заново, оператор-мутатор (`Х.Вставить(…)`, `Х.Колонки.Добавить(…)`)
  дополняет накопленный — поэтому поле видно только после своей вставки, а не по всей области
  видимости. Про вывод типов не знает: и присваиваемые типы, и изменения мутаторов получает
  колбэками, места тех и других — списками позиций. Применяется в `identifierType`; неприменим
  (и тогда работает объединение по всей области видимости из `inferVariable`) для переменных
  модуля, использования из другого документа и операторов, которых нет в графе отдельным
  оператором (связывание в `Для Каждого`).
  **`GuardConditionNarrowing`** — сужение на рёбрах веток условия: `ТипЗнч(Х) = Тип("Имя")`
  и `Х <> Неопределено`, в обе стороны сравнения и на обеих ветках. Конъюнкция сужает,
  дизъюнкция — нет; на ложной ветке сужает только условие из одной проверки. Охранное
  предложение (`Если … Тогда Возврат; КонецЕсли;`) получается само: до кода за условием
  доходит только ложная ветка.
  **`ExpressionAtPosition`** — наименьшее охватывающее позицию выражение → `BslExpression`.
- **`scope/`** — `UseDirectiveScanner` (директивы `#use` OneScript). Глобальная резолюция имён —
  в `registry/GlobalScopeProvider`.
- **`symbol/`** — обёртки несорсовых сущностей: **`PlatformMemberSymbol`** (член платформенного/
  конфигурационного типа или глобал, несёт `MemberDescriptor`), **`ConstructorCallSymbol`**.
- **`oscript/`** — источники типов OneScript: `OScriptLibraryIndex`, `OScriptModuleMembersProvider`
  (регистрирует USER-типы и члены .os), обнаружение библиотек за один обход дерева
  (`OScriptLibraryScanner` поверх корней из `OScriptLibraryRootResolver`; `DirContents` —
  примитив чтения каталога; `ConventionalLibraryDiscovery` — распознавание по соглашению;
  `LibConfigParser` — разбор `lib.config`); подпакеты `extends_/` (наследование классов:
  `OScriptExtends`, `TypeRelationIndex`), `annotations/`, `autumn/` (DI-фреймворк Autumn).
- **`util/`** — `SignatureSelection` (выбор перегрузки по числу/типам аргументов).

## Поток вывода типа (упрощённо)

`TypeService.expressionTypesAt() / typesAt()` → `ExpressionTypeInferencer` (через
`ExpressionAtPosition`) → диспетчер по узлу `BslExpression`: литералы → фикс. тип; идентификаторы →
`ReferenceResolver` + `SymbolTypeIndex`/`GlobalScopeProvider`; вызовы → тип получателя +
`TypeRegistry.getMembers()` → возвращаемые типы; конструкторы/имена модулей → `GlobalScopeProvider`.
Итог — `TypeSet`.

## Правки в этом каталоге

- Не создавай дубль `TypeRef` — получай канонический через `TypeRegistry` (интернирование).
- Новый источник членов типа — это новый `MemberSource`/провайдер в `registry/`, а не правка
  существующих типов: модель `Type`/`TypeRef`/`TypeSet` **неизменяемая**.
- Кэш членов в `TypeRegistry` инвалидируется по epoch при регистрации/снятии источников —
  при изменении регистрации убедись, что epoch обновляется.
