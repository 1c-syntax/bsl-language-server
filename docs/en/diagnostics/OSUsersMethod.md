# Using method OSUsers (OSUsersMethod)

 |        Type        | Scope |  Severity  | Activated<br>by default | Minutes<br>to fix |     Tags     |
 |:------------------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:------------:|
 | `Security Hotspot` | `BSL` | `Critical` |             `Yes`             |          `15`           | `suspicious` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Using method may carry a malicious function.

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Pass-the-hash attack](https://en.wikipedia.org/wiki/Pass_the_hash)

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
