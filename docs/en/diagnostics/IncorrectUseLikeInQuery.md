# Incorrect use of 'LIKE' (IncorrectUseLikeInQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

When using the operator `LIKE` in the query text, it is allowed to use only
- constant string literals
- query parameters

It is forbidden to form a template string using calculations, use string concatenation using the query language.

Queries in which the control characters of the operator template `LIKE` are in query fields or in calculated expressions are interpreted differently on different DBMSs.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

### String concatenation by language features

Allowed:

```
Field LIKE "123%"
```

Not allowed:

```
Field LIKE "123" + "%"
Field LIKE Table.Template
```

### Operator template control characters LIKE are found in query fields or in calculated expressions

For example, instead of:

```
Query = New Query("
|SELECT
|    Goods.Ref
|FROM
|    Catalog.Goods AS Goods
|WHERE
|    Goods.Country.Description LOKE &NameTemplate + "_"
|");

Query.SetParameter("NameTemplate", "FU");
```

Nessesary to use:

```
Query = New Query("
|SELECT
|    Goods.Ref
|FROM
|    Catalog.Goods AS Goods
|WHERE
|    Goods.Country.Description LOKE &NameTemplate
|");

Query.SetParameter("NameTemplate", "FU_");
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

- [Standard. Features of use in operator requests LIKE (RU)](https://its.1c.ru/db/v8std/content/726/hdoc?ysclid=l3g3fkmxsx)
