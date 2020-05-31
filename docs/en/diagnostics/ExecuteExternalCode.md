# Executing of external code on the server (ExecuteExternalCode)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Vulnerability` | `BSL` | `Critical` | `Yes` | `1` | `error`<br/>`standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

It is dangerous to use not only direct execution of code written in the Enterprise mode, but also algorithms where `Execute` or `Eval` code executes the code in server methods.
It is forbidden to use the methods `Execute ` and `Eval` in server methods of modules: forms, commands, objects, etc.

**The restriction does not apply to code executed on the client.**

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [Restrictions on the use of Execute and Eval on the server (RU)](https://its.1c.ru/db/v8std#content:770:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ExecuteExternalCode-off
// BSLLS:ExecuteExternalCode-on
```

### Parameter for config

```json
"ExecuteExternalCode": false
```
