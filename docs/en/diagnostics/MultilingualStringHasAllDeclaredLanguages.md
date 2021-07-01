# There is a localized text for all languages declared in the configuration (MultilingualStringHasAllDeclaredLanguages)

|  Type   | Scope | Severity | Activated<br>by default | Minutes<br>to fix |            Теги             |
|:-------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------:|
| `Error` | `BSL` | `Minor`  |             `Yes`             |           `2`           | `error`<br>`localize` |

## Parameters


|        Name         |   Type   |     Description      | Значение<br>по умолчанию |
|:-------------------:|:--------:|:--------------------:|:------------------------------:|
| `declaredLanguages` | `String` | `Declared languages` |              `ru`              |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

NStr in a multilingual configuration has different fragments for different languages. If you start a session under a language code that is not in the string passed to NStr, it will return an empty string.

## Sources

- [localization requirements](https://its.1c.ru/db/v8std/content/763/hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MultilingualStringHasAllDeclaredLanguages-off
// BSLLS:MultilingualStringHasAllDeclaredLanguages-on
```

### Parameter for config

```json
"MultilingualStringHasAllDeclaredLanguages": {
    "declaredLanguages": "ru"
}
```
