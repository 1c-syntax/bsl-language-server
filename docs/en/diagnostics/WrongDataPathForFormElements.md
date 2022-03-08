# Form fields do not have a data path (WrongDataPathForFormElements)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

# Form fields do not have a data path (WrongDataPathForFormElements)

|  Type   | Scope |  Severity  | Activated<br>by default | Minutes<br> to fix |      Tags       |
|:-------:|:-----:|:----------:|:-----------------------------:|:------------------------:|:---------------:|
| `Error` | `BSL` | `Critical` |             `Yes`             |           `5`            | `unpredictable` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
When actively reworking a form or associated metadata, there may be elements on the form that do not have an associated data element. Problems can arise after deleting metadata or metadata attributes, or when changing the main form attribute. As a result, such a form field will not be displayed. Errors occur with forms when extended validation is enabled in the 1C Configurator.
```
Справочник.Контрагенты.Форма.ФормаЭлемента.Форма Неразрешимые ссылки на объекты метаданных (12)
```
In this case, in form files from XML unloading, the value of the "Data path" property begins with a "~" sign, for example, for the "Description" field: "<DataPath>~Object.Description</DataPath>".

When you manually change the query of a dynamic list, the "Data path" property of its fields on the form is cleared. This leads to a breakdown of the connection between the form element (table column) and the dynamic list field, and it disappears from the table on the form.

In form files, when unloaded to XML, the value of the "Data path" property starts with a "~" character, for example, for the "Description" field: `<DataPath> ~ List.Description </DataPath>`.

For command bar buttons associated with the "Data" property with a standard dynamic list field, for example, for a button with a filled "Ref" value in the "CurrentData" property: `<DataPath> ~ Items.List.CurrentData.Ref </DataPath>`.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
- [General requirements - Standards 1C (RU)](https://its.1c.ru/db/v8std#content:467:hdoc)
