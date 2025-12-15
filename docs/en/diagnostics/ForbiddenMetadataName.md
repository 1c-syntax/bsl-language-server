# Metadata object has a forbidden name (ForbiddenMetadataName)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

It is forbidden to use the names of metadata objects (and their attributes and tabular sections), which are used when naming query tables (for example, Document, Catalog).

Using such names can lead to errors in the execution of the query, and also make it difficult to use the query designer and reduce the clarity of the query text.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Wrong name
- `Catalog.Catalog`
- `Catalog.MyCatalog.Attribute.Document`
- `InformationRegister.MyRegister.Dimension.Documents`

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* [Standard: Organization of data storage (RU). Name, Synonym, Comment](https://its.1c.ru/db/v8std#content:474:hdoc:2.5)
