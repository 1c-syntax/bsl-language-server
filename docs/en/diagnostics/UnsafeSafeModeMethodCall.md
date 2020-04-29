# Unsafe SafeMode method call (UnsafeSafeModeMethodCall)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Blocker` | `Yes` | `1` | `standard`<br/>`deprecated`<br/>`error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Developers make mistakes using the implicit conversion SafeMode() to Boolean type

In fact, this method can return the profile name with a string.

Use SafeMode() <> False or SafeMode() = False
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Incorrect:
```
If SafeMode() Then
// some code
EndIf;
```
Correct:
```
If SafeMode() = True Then
// some code
EndIf;
```
## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информаця: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
Source: [SafeMode method](https://its.1c.ru/db/metod8dev#content:5293:hdoc:izmenenie_bezopasnyjrezhim)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:UnsafeSafeModeMethodCall-off
// BSLLS:UnsafeSafeModeMethodCall-on
```

### Parameter for config

```json
"UnsafeSafeModeMethodCall": false
```
