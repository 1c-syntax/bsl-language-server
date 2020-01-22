# Escaping Code from Diagnostic

Статический анализатор обнаруживает проблемы, ошибки и недочеты в коде в соответствии с заложенными в него правилами (диагностиками). Как и везде, в коде решения могут возникать ситуации, когда необходимо отойти от правил. Данные ситуации могут возникать по разным причинам: как в связи с архитектурными особенностями решения, так и в следствии невозможности (по разным причинам) изменить код для соответствия требованиям.

Для того, чтобы не приходилось каждый раз вручную отмечать замечание как неактуальное, BSL LS предоставляет функциональность, посредством которой можно скрыть или заэкранировать отдельные участки кода от срабатывания той или иной диагностики.

## Description

To hide part of the code from the BSL LS analyzer, you must add a special comment to the code.
 The escaping comment is formed as follows: `[Prefix][:DiagnosticKey]-[ActivationFlag]`. Now in more detail.

- `Prefix` always is `// BSLLS`
- `DiagnosticKey` can be found in the [list of diagnostics](../diagnostics/index.md) by description.
- `ActivationFlag` string parameter if giagnostic is On or Off. Supported Russian (`вкл` and `выкл`) and English (`on` and `off`).

To disable **ALL** diagnostics for part of the code, you must omit the diagnostic key.

## Examples and use cases

### Disable all diagnostics in the module

Для отключения всех диагностик в модуле, т.е. по-сути скрыть модуль от анализатора BSL LS, необходимо в начале модуля вставить комментарий `// BSLLS-off` (или `// BSLLS-выкл`)

### Disable specific diagnostics in the module

Для отключения конкретных диагностик в модуле (возьмем для примера когнитивную сложность `CognitiveComplexity` и ограничение на размер метода `MethodSize`), необходимо в начале модуля вставить комментарий `// BSLLS:CognitiveComplexity-off` и `// BSLLS:MethodSize-off` (или `// BSLLS:CognitiveComplexity-выкл` и `// BSLLS:MethodSize-выкл`)

### Disable all diagnostics for code block

Если необходимо отключить диагностики для участка кода, оставив возможность BSL LS анализировать оставшие, необходимо `обернуть` скрываемый участок кода по примеру

```bsl
// BSLLS-off
Процедура СкрываемаяОтBSLLSПроцедура()
    // Невидимое содержимое процедуры
КонецПроцедуры
// BSLLS-on

Процедура ВидимаяBSLLSПроцедура()
    // Видимое содержимое процедуры
КонецПроцедуры
```

### Disable specific diagnostics for code block

Если необходимо отключить конкретные диагностики (возьмем для примера когнитивную сложность `CognitiveComplexity` и ограничение на размер метода `MethodSize`) для участка кода, необходимо `обернуть` скрываемый участок кода по примеру

```bsl
// BSLLS:MethodSize-off
Процедура СкрываемаяОтMethodSizeПроцедура()
    // Очень длинное тело метода
КонецПроцедуры
// BSLLS:MethodSize-on

// BSLLS:CognitiveComplexity-off
Процедура СкрываемаяОтCognitiveComplexityПроцедура()
    // Очень сложное и непонятное содержимое
КонецПроцедуры
// BSLLS:CognitiveComplexity-on
```

Поддерживается вложение `оберток`, т.е. возможно экранирование блока кода от нескольких диагностик, пример

```bsl
// BSLLS:CognitiveComplexity-off
// BSLLS:MethodSize-off
Процедура СкрываемаяОтMethodSizeCognitiveComplexityПроцедура()
    // Очень длинное тело метода, непонятное никому
КонецПроцедуры
// BSLLS:MethodSize-on

Процедура СкрываемаяОтCognitiveComplexityПроцедура()
    // Очень сложное и непонятное содержимое, но не длинное
КонецПроцедуры
// BSLLS:CognitiveComplexity-on
```

### Disable single line diagnostics

Для экранирование одной строки можно использовать `обертку` как в примере выше, но удобнее использовать `висячий комментарий`, т.е. комментарий, расположенный в конце строки, пример

```bsl
Процедура ВидимаяBSLLS()
    // С помощью висячего комментария скрыта следующая строка от диагностики "Каноническое написание ключевых слов"
    Если Истина тогда // BSLLS:CanonicalSpellingKeywords-выкл
        // Видимое содержимое
    КонецЕсли;
КонецПроцедуры
```
