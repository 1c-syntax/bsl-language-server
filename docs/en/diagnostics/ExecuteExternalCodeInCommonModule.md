# Executing of external code in a common module on the server (ExecuteExternalCodeInCommonModule)

|        Type        | Scope |  Severity  |    Activated<br>by default    |    Minutes<br>to fix    |               Tags                |
|:------------------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
| `Security Hotspot` | `BSL` | `Critical` |             `Yes`             |          `15`           |    `badpractice`<br>`standard`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

When you develop applications, note that not only execution of a code written in the Enterprise mode is unsafe, but also places, where the `Execute` or `Eval` methods are used to execute the code created based on parameters passed to server functions and procedures.  
If the execution of arbitrary code Is necessary then it has to be preliminarily checked.

**This restriction is not applicable to the code being executed on the client.**

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Restrictions on the use of Execute and Eval on the server (RU)](https://its.1c.ru/db/v8std#content:770:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ExecuteExternalCodeInCommonModule-off
// BSLLS:ExecuteExternalCodeInCommonModule-on
```

### Parameter for config

```json
"ExecuteExternalCodeInCommonModule": false
```
