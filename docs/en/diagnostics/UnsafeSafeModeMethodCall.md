# Unsafe SafeMode method call (UnsafeSafeModeMethodCall)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Blocker` | `Yes` | `1` | `deprecated`<br/>`error` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In "1C: Enterprise 8.3" the global context method SafeMode () returns the type String,
if safe mode was set with the name of the security profile.

Using the Safe Mode () method,
 in which the result is implicitly converted to a Boolean type is unsafe,
 must be corrected for the code with an explicit comparison of the result with the value False.
 Thus, with the installed security profile, the code will be executed in the same way as in the safe mode.

Use SafeMode() <> False
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
