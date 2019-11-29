# Method size  (MethodSize)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Major` | `Yes` | `30` | `badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `maxMethodSize` | `int` | ```Max method line count.``` | ```200``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MethodSize-off
// BSLLS:MethodSize-on
```

### Parameter for config

```json
"MethodSize": {
    "maxMethodSize": 200
}
```
