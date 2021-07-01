# Code out of region (CodeOutOfRegion)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `standard` |

## Parameters 


|           Name           |   Type    |         Description         | Default value |
|:------------------------:|:---------:|:---------------------------:|:-------------:|
| `checkUnknownModuleType` | `Boolean` | `Check unknown module type` |    `false`    |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

The code of module should be structured and divided into regions.  
The requirement to structure code by regions is to improve code readability and maintainability and development by group of authors (developers) and in finalizing application solutions on specific implementations.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

```bsl
#Region <RegionName>
```

The standard describes only 10 region names, the names of nested regions are not checked.

Correct:
```bsl
#Region Private
#Region Print
// Methods code
#EndRegion
#Region Other
// Methods code
#EndRegion
#EndRegion
```

Name matching table (full in [source code](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/utils/Keywords.java#L255)):
| RU                                      | EN                           |
| --------------------------------------- | ---------------------------- |
| ПрограммныйИнтерфейс                    | Public                       |
| СлужебныйПрограммныйИнтерфейс           | Internal                     |
| СлужебныеПроцедурыИФункции              | Private                      |
| ОбработчикиСобытий                      | EventHandlers                |
| ОбработчикиСобытийФормы                 | FormEventHandlers            |
| ОбработчикиСобытийЭлементовШапкиФормы   | FormHeaderItemsEventHandlers |
| ОбработчикиКомандФормы                  | FormCommandsEventHandlers    |
| ОписаниеПеременных                      | Variables                    |
| Инициализация                           | Initialize                   |
| ОбработчикиСобытийЭлементовТаблицыФормы | FormTableItemsEventHandlers  |

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* Reference [Code conventions. Module structure](https://its.1c.ru/db/v8std#content:455:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CodeOutOfRegion-off
// BSLLS:CodeOutOfRegion-on
```

### Parameter for config

```json
"CodeOutOfRegion": {
    "checkUnknownModuleType": false
}
```
