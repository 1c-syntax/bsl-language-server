<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# diagnostics/ — движок диагностик (150+ правил)

Ядро статического анализа кода 1С. Одна диагностика = **несколько связанных файлов** (см. ниже).
**Следуй готовым гайдам в `docs/contributing/`, не выдумывай свой процесс.** Общая картина —
в корневом [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Спутники одной диагностики (`XxxDiagnostic`)

1. Java-класс `XxxDiagnostic.java` — реализует `BSLDiagnostic`, помечен `@DiagnosticMetadata`.
2. `XxxDiagnostic_ru.properties` и `_en.properties` в `src/main/resources/.../diagnostics/`
   (ключи `diagnosticName`, `diagnosticMessage`, описания параметров; **живая кириллица**, не экранируй).
3. `docs/diagnostics/Xxx.md` (ru) и `docs/en/diagnostics/Xxx.md` (en) — встраиваются в ресурсы
   задачей `generateDiagnosticDocs`.
4. Тест `XxxDiagnosticTest` + фикстуры в `src/test/resources/diagnostics/`.

Гайды: [DiagnosticExample](../../../../../../../../../docs/contributing/DiagnosticExample.md) ·
[DiagnosticStructure](../../../../../../../../../docs/contributing/DiagnosticStructure.md) ·
[DiagnostcAddSettings](../../../../../../../../../docs/contributing/DiagnostcAddSettings.md) ·
[DiagnosticQuickFix](../../../../../../../../../docs/contributing/DiagnosticQuickFix.md).

## Базовые классы — что наследовать

`BSLDiagnostic` (контракт `getDiagnostics(DocumentContext)`) → `AbstractDiagnostic` (реализует
жизненный цикл: чистит storage → вызывает твой `check()`). Выбирай базовый класс по типу анализа:

| Базовый класс | Когда |
|---|---|
| `AbstractVisitorDiagnostic` | обход AST BSL визитором (`visitXxx()`) — самый частый случай |
| `AbstractListenerDiagnostic` | обход AST BSL слушателем (`enterXxx`/`exitXxx`) |
| `AbstractSDBLVisitorDiagnostic` / `…ListenerDiagnostic` | анализ языка запросов (SDBL) |
| `AbstractSymbolTreeDiagnostic` | анализ по дереву символов (методы/модули/переменные) |
| `AbstractExpressionTreeDiagnostic` | семантика выражений (`visitTopLevelExpression()`) |
| `AbstractMetadataDiagnostic` | свойства объектов метаданных (`checkMetadata(MD)`) |
| `AbstractFindMethodDiagnostic` | вызовы методов по regex |
| `AbstractMagicValueDiagnostic`, `AbstractMultilingualStringDiagnostic`, `AbstractCommonModuleNameDiagnostic`, `AbstractExecuteExternalCodeDiagnostic` | специализированные хелперы |

Выявленные проблемы добавляй через **`DiagnosticStorage`**: `addDiagnostic(ctx|token|range[, message])`.
Доп. данные для quick fix — `DiagnosticAdditionalData`.

## Метаданные — `metadata/`

**`@DiagnosticMetadata`** (на классе): `type`, `severity`, `scope`, `modules`, `minutesToFix`,
`activatedByDefault`, `compatibilityMode`, `tags`, `canLocateOnProject`, `lspSeverity` и др.
Энумы: `DiagnosticType` (ERROR/CODE_SMELL/VULNERABILITY/SECURITY_HOTSPOT),
`DiagnosticSeverity` (INFO…BLOCKER), `DiagnosticScope` (ALL/BSL/OS), `DiagnosticTag`,
`DiagnosticCompatibilityMode`. `DiagnosticCode` — код = имя класса без суффикса `Diagnostic`.
Подпакет `metadata/` — чистый словарь без зависимостей (аннотация + энумы), поэтому от него
может зависеть и `configuration` (десериализация переопределений метаданных).

## Рантайм-дескрипторы — `info/`

`DiagnosticInfo` — рантайм-обёртка над метаданными (i18n из `.properties`/`.md`, маппинг в
LSP-severity и LSP-теги); зависит от `configuration`. `DiagnosticParameterInfo` — описание
параметра, извлекаемое рефлексией (включая суперклассы). Лежат отдельно от словаря `metadata/`,
чтобы тот оставался листом без обратных зависимостей.

## Параметры

Поле с **`@DiagnosticParameter`** (`type`, `defaultValue`) + одноимённый ключ в `.properties`.
Значения из конфигурации применяются через `configure(Map)` (см. `DiagnosticBeanPostProcessor`);
`DiagnosticParameterInfo` извлекает их рефлексией (включая суперклассы).

## Инфраструктура — `infrastructure/`

Диагностики — Spring-бины (prototype) c `@DiagnosticMetadata`; обнаруживаются автоматически.
`DiagnosticBeanPostProcessor` инжектит `DiagnosticInfo` и применяет конфигурацию.
`DiagnosticInfos`/`DiagnosticInfosFactory` (workspace-scoped) держат `code/class → DiagnosticInfo`
и пересчитывают при смене конфигурации. `@Disabled` исключает диагностику из регистрации.

## Quick fix и подпакеты

- **`QuickFixProvider`** — диагностика опционально реализует
  `getQuickFixes(List<Diagnostic>, CodeActionParams, DocumentContext)`.
- `typo/` — инфраструктура проверки орфографии (JLanguageTool: `JLanguageToolPool`,
  `CheckedWordsHolder`). `platform/` — интроспекция вызовов платформенного API (`PlatformMemberCalls`).

## Прогон одной диагностики при разработке

```bash
./gradlew test --tests "*XxxDiagnosticTest"
```
