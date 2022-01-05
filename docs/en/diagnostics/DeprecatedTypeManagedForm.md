# Deprecated ManagedForm type (DeprecatedTypeManagedForm)

|      Type      |    Scope    |     Severity     |    Activated<br>by default    |    Minutes<br>to fix    |               Tags               |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:--------------------------------:|
| `Code smell` |             `BSL`             | `Info` |              `Yes`              |                 `1`                 |    `standard`<br>`deprecated`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Starting from the platform version 8.3.14, the "ManagedForm" type has been renamed, now it is correct to use the "ClientApplicationForm"

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Source: [Platform 8.3.16 changelog (RU)](https://dl03.1c.ru/content/Platform/8_3_16_1148/1cv8upd_8_3_16_1148.htm)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedTypeManagedForm-off
// BSLLS:DeprecatedTypeManagedForm-on
```

### Parameter for config

```json
"DeprecatedTypeManagedForm": false
```
