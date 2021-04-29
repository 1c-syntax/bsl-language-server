# Deprecated client application method. (DeprecatedMethods8310)

 Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags 
 :-: | :-: | :-: | :-: | :-: | :-: 
 `Code smell` | `BSL` | `Info` | `Yes` | `1` | `deprecated` 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
The following global context methods are deprecated and are not recommended since platform version 8.3.10:
```bsl
SetShortApplicationCaption();
GetShortApplicationCaption();
SetClientApplicationCaption();
GetClientApplicationCaption();
ClientApplicationBaseFontCurrentVariant();
ClientApplicationInterfaceCurrentVariant().
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Source: [Version 8.3.10 changelog](https://dl03.1c.ru/content/Platform/8_3_10_2699/1cv8upd.htm)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedMethods8310-off
// BSLLS:DeprecatedMethods8310-on
```

### Parameter for config

```json
"DeprecatedMethods8310": false
```
