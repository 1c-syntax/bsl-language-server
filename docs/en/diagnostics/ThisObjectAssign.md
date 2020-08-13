# ThisObject assign (ThisObjectAssign)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Error` | `BSL` | `Blocker` | `Yes` | `1` | `error`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

In managed form modules and common modules, there should not be a variable named "ThisObject".

Часто ошибка появляется при поднятии версии режима совместимости конфигурации т.к. в версиях до 8.3.3 свойство "ЭтотОбъект" у управляемых форм и общих модулей отсутствовало. И могло быть использовано как переменная.

## Examples

Wrong:

```bsl
ThisObject = FormAttributeToValue("Object");
```

## Sources

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:ThisObjectAssign-off
// BSLLS:ThisObjectAssign-on
```

### Parameter for config

```json
"ThisObjectAssign": false
```
