# Consecutive empty lines (ConsecutiveEmptyLines)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Yes` | `1` | `badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `allowedEmptyLinesCount` | `Integer` | ```Minimal allowed consecutive empty lines``` | ```2``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:<DiagnosticKey>-off
// BSLLS:<DiagnosticKey>-on
```

### Parameter for config

```json
"<DiagnosticKey>": <DiagnosticConfig>
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:ConsecutiveEmptyLines-off
// BSLLS:ConsecutiveEmptyLines-on
```

### Parameter for config

```json
"ConsecutiveEmptyLines": {
    "allowedEmptyLinesCount": 2
}
```
