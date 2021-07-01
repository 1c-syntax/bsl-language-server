# Using method OSUsers (OSUsersMethod)

|        Type        | Scope |  Severity  | Activated<br>by default | Minutes<br>to fix |     Теги     |
|:------------------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:------------:|
| `Security Hotspot` | `BSL` | `Critical` |             `Yes`             |          `15`           | `suspicious` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Using method may carry a malicious function.

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Pass-the-hash attack](https://ru.wikipedia.org/wiki/%D0%90%D1%82%D0%B0%D0%BA%D0%B0_Pass-the-hash)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:OSUsersMethod-off
// BSLLS:OSUsersMethod-on
```

### Parameter for config

```json
"OSUsersMethod": false
```
