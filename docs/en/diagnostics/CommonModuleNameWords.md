# Unrecommended common module name (CommonModuleNameWords)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Info` | `Yes` | `5` | `standard` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `words` | `String` | ```Unrecommended words``` | ```процедуры|procedures|функции|functions|обработчики|handlers|модуль|module|функциональность|functionality``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CommonModuleNameWords-off
// BSLLS:CommonModuleNameWords-on
```

### Parameter for config

```json
"CommonModuleNameWords": {
    "words": "процедуры|procedures|функции|functions|обработчики|handlers|модуль|module|функциональность|functionality"
}
```
