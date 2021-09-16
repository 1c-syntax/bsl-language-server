# The check box «Set permissions for new objects» should only be selected for the FullAccess role (SetPermissionsForNewObjects)

|      Type       | Scope |  Severity  |    Activated<br>by default    |    Minutes<br>to fix    |                        Tags                         |
|:---------------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:---------------------------------------------------:|
| `Vulnerability` | `BSL` | `Critical` |             `Yes`             |           `1`           |       `standard`<br>`badpractice`<br>`design`       |

## Parameters


|         Name          |   Type   |             Description             |      Default value       |
|:---------------------:|:--------:|:-----------------------------------:|:------------------------:|
| `namesFullAccessRole` | `String` | `Name of the role with full rights` | `FullAccess,ПолныеПрава` |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
When adding a new role, the "Set permissions for new objects" attribute may be set incorrectly, which will lead to the accumulation of rights in this role for all objects added after it and extra rights for users with this role.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources

* [Standard: Setting rights for new objects and object fields](https://its.1c.ru/db/v8std/content/532/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:SetPermissionsForNewObjects-off
// BSLLS:SetPermissionsForNewObjects-on
```

### Parameter for config

```json
"SetPermissionsForNewObjects": {
    "namesFullAccessRole": "FullAccess,ПолныеПрава"
}
```
