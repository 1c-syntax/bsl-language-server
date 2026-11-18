# Privileged mode in exported method (ExportMethodPrivilegedMode)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

This diagnostic detects explicit calls to `SetPrivilegedMode(True)` or
`УстановитьПривилегированныйРежим(Истина)` inside exported procedures
and functions.

Enabling privileged mode bypasses normal access-right checks and may
introduce security risks when exported methods are called by other code.

Calls in non-exported methods, calls with `False`, and calls using
variable arguments are not reported by this diagnostic.


## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Incorrect:

```bsl
Procedure UpdateData() Export
    SetPrivilegedMode(True);
EndProcedure
```

Correct:

```bsl
Procedure UpdateData() Export
    // Perform operations with normal access-right checks.
EndProcedure
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [GitHub Issue #1755](https://github.com/1c-syntax/bsl-language-server/issues/1755)
- [1C Standard: Using Privileged Mode](https://its.1c.ru/db/v8std/content/485/hdoc)
