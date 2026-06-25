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
- **`registry/`** — источники и резолюция типов. **`TypeRegistry`** (workspace-scoped) —
  интернирование `TypeRef`, индекс алиасов, **мультиисточниковое** расширение членов
  (`FileType → TypeRef → List<MemberSource>`), мемоизация `getMembers()` с epoch-инвалидацией,
  синтетический `GLOBAL_CONTEXT`. **`PlatformTypesProvider`** и наследники (`Builtin…`,
  `BslContext…`, `Configuration…`, `GlobalContext…`) регистрируют платформенные/конфигурационные
  типы. **`GlobalScopeProvider`** — глобальные функции/свойства, имена классов, ключевые слова
  (JSON `builtin-globals.json`), карта `moduleTypeByUri ↔ moduleUriByType`.
  Также `MemberMetadataIndex`, `StandardAttributesResolver`.
- **`index/`** — индексы «символ → тип»: **`SymbolTypeIndex`** (возвращаемые типы методов,
  типы параметров), `InferredVariableTypeIndex`, `CallStatementByReceiverIndex`,
  `EventContractsIndex`, `WorkspaceSymbolIndex`.
- **`inferencer/`** — вывод типов. **`ExpressionTypeInferencer`** (workspace-scoped) — `infer()`
  по дереву `BslExpression` (а не сырому ANTLR); диспетчеризация по узлу (LITERAL/IDENTIFIER/CALL/
  BINARY_OP/…), защита от циклов, устойчив к битым выражениям (→ `UNKNOWN`/`TypeSet.EMPTY`).
  **`ExpressionAtPosition`** — наименьшее охватывающее позицию выражение → `BslExpression`.
- **`scope/`** — `UseDirectiveScanner` (директивы `#use` OneScript). Глобальная резолюция имён —
  в `registry/GlobalScopeProvider`.
- **`symbol/`** — обёртки несорсовых сущностей: **`PlatformMemberSymbol`** (член платформенного/
  конфигурационного типа или глобал, несёт `MemberDescriptor`), **`ConstructorCallSymbol`**.
- **`oscript/`** — источники типов OneScript: `OScriptLibraryIndex`, `OScriptModuleMembersProvider`
  (регистрирует USER-типы и члены .os), обнаружение библиотек (`ConventionalLibraryDiscovery`,
  `LibConfigDiscovery`, `LibConfigParser`); подпакеты `extends_/` (наследование классов:
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
