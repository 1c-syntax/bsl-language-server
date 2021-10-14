# Checking the canonical spelling of keywords in a query (CanonicalSpellingKeywordsInQuery)

|     Type     | Scope | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:----------:|
| `Code smell` | `BSL` |  `Info`  |             `Yes`             |           `1`           | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

### Keywords

| RU                | EN                |
|-------------------|-------------------|
|NULL               |NULL               |
|---                |OF                 |
|ПО                 |ON                 |
|АВТОНОМЕРЗАПИСИ    |RECORDAUTONUMBER   |
|АВТОУПОРЯДОЧИВАНИЕ |AUTOORDER          |
|БУЛЕВО             |BOOLEAN            |
|В                  |IN                 |
|ВНЕШНЕЕ            |OUTER              |
|ВНУТРЕННЕЕ         |INNER              |
|ВОЗР               |ASC                |
|ВСЕ                |ALL                |
|ВЫБОР              |CASE               |
|ВЫБРАТЬ            |SELECT             |
|ВЫРАЗИТЬ           |CAST               |
|ГДЕ                |WHERE              |
|ГОД                |YEAR               |
|ГРУППИРУЮЩИМ       |GROUPING           |
|ДАТА               |DATE               |
|ДАТАВРЕМЯ          |DATETIME           |
|ДЕКАДА             |TENDAYS            |
|ДЕНЬ               |DAY                |
|ДЕНЬГОДА           |DAYOFYEAR          |
|ДЕНЬНЕДЕЛИ         |WEEKDAY            |
|ДЛЯ                |EN                 |
|ДОБАВИТЬКДАТЕ      |DATEADD            |
|ЕСТЬ               |IS                 |
|ЕСТЬNULL           |ISNULL             |
|ЗНАЧЕНИЕ           |VALUE              |
|И                  |AND                |
|ИЕРАРХИИ           |HIERARCHY          |
|ИЕРАРХИЯ           |HIERARCHY          |
|ИЗ                 |FROM               |
|ИЗМЕНЕНИЯ          |UPDATE             |
|ИЛИ                |OR                 |
|ИМЕЮЩИЕ            |HAVING             |
|ИНАЧЕ              |ELSE               |
|ИНДЕКСИРОВАТЬ      |INDEX              |
|ИТОГИ              |TOTALS             |
|Истина             |TRUE               |
|КАК                |AS                 |
|КВАРТАЛ            |QUARTER            |
|КОГДА              |WHEN               |
|КОЛИЧЕСТВО         |COUNT              |
|КОНЕЦ              |END                |
|КОНЕЦПЕРИОДА       |ENDOFPERIOD        |
|ЛЕВОЕ              |LEFT               |
|Ложь               |FALSE              |
|МАКСИМУМ           |MAX                |
|МЕЖДУ              |BETWEEN            |
|МЕСЯЦ              |MONTH              |
|МИНИМУМ            |MIN                |
|МИНУТА             |MINUTE             |
|НАБОРАМ            |SETS               |
|НАЧАЛОПЕРИОДА      |BEGINOFPERIOD      |
|НЕ                 |NOT                |
|НЕДЕЛЯ             |WEEK               |
|НЕОПРЕДЕЛЕНО       |UNDEFINED          |
|ОБЩИЕ              |OVERALL            |
|ОБЪЕДИНИТЬ         |UNION              |
|ПЕРВЫЕ             |TOP                |
|ПЕРИОДАМИ          |PERIODS            |
|ПОДОБНО            |LIKE               |
|ПОДСТРОКА          |SUBSTRING          |
|ПОЛНОЕ             |FULL               |
|ПОЛУГОДИЕ          |HALFYEAR           |
|ПОМЕСТИТЬ          |INTO               |
|ПРАВОЕ             |RIGHT              |
|ПРЕДСТАВЛЕНИЕ      |PRESENTATION       |
|ПРЕДСТАВЛЕНИЕССЫЛКИ|REFPRESENTATION    |
|ПУСТАЯТАБЛИЦА      |EMPTYTABLE         |
|РАЗЛИЧНЫЕ          |DISTINCT           |
|РАЗНОСТЬДАТ        |DATEDIFF           |
|РАЗРЕШЕННЫЕ        |ALLOWED            |
|СГРУППИРОВАНОПО    |GROUPEDBY          |
|СГРУППИРОВАТЬ      |GROUP              |
|СЕКУНДА            |SECOND             |
|СОЕДИНЕНИЕ         |JOIN               |
|СПЕЦСИМВОЛ         |ESCAPE             |
|СРЕДНЕЕ            |AVG                |
|ССЫЛКА             |REFS               |
|СТРОКА             |STRING             |
|СУММА              |SUM                |
|ТИП                |TYPE               |
|ТИПЗНАЧЕНИЯ        |VALUETYPE          |
|ТОГДА              |THEN               |
|ТОЛЬКО             |ONLY               |
|УБЫВ               |DESC               |
|УНИЧТОЖИТЬ         |DROP               |
|УПОРЯДОЧИТЬ        |ORDER              |
|ЧАС                |HOUR               |
|ЧИСЛО              |NUMBER             |   

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Источник: [Стандарт: Оформление текстов запросов](https://its.1c.ru/db/v8std#content:437:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:CanonicalSpellingKeywordsInQuery-off
// BSLLS:CanonicalSpellingKeywordsInQuery-on
```

### Parameter for config

```json
"CanonicalSpellingKeywordsInQuery": false
```
