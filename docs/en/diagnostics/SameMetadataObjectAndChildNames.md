# Same metadata object and child name (SameMetadataObjectAndChildNames)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

# Same metadata object and child name (SameMetadataObjectAndChildNames)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |                    Tags                     |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:-------------------------------------------:|
| `Error` |             `BSL`             | `Critical` |              `Yes`              |                `30`                 |       `standard`<br>`sql`<br>`design`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

For child metadata objects, such as attributes, dimensions, resources, tabular sections (and their attributes), it is not recommended to use names that match the names of the owner objects, since this can lead to errors in queries.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect names

```
Catalog.Contractors.TabularSection.Contractors
InformationRegister.SubordinateDocuments.Dimension.SubordinateDocuments
Document.Container.TabularSection.Container. Attribute.Container
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Standard: Organization of data storage (RU). Name, Synonym, Comment](https://its.1c.ru/db/v8std#content:474:hdoc:2.4)
