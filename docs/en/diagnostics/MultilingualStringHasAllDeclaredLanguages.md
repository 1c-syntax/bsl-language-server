# There is a localized text for all languages declared in the configuration (MultilingualStringHasAllDeclaredLanguages)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Minor` | `Yes` | `2` | `error` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `declaredLanguages` | `String` | ```Declared languages``` | ```ru``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

# There is a localized text for all languages declared in the configuration

NStr in a multilingual configuration has different fragments for different languages.
If you start a session under a language code that is not in the string passed to NStr, it will return an empty string.

Source: [localization requirements] (https://its.1c.ru/db/v8std/content/763/hdoc)

## Options

* `Declared languages` -` String` - Comma-separated lines of language codes that support configuration. For example: `ru, en`

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
