# Проверка канонического написания ключевых слов в запросе (CanonicalSpellingKeywordsInQuery)

|      Тип      |    Поддерживаются<br>языки    |     Важность     |    Включена<br>по умолчанию    |    Время на<br>исправление (мин)    |    Теги    |
|:-------------:|:-----------------------------:|:----------------:|:------------------------------:|:-----------------------------------:|:----------:|
| `Дефект кода` |             `BSL`             | `Информационный` |              `Да`              |                 `1`                 | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

В запросах ключевые слова пишутся канонически - заглавными буквами.

### Ключевые слова

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

## Источники

* Источник: [Стандарт: Оформление текстов запросов](https://its.1c.ru/db/v8std#content:437:hdoc)

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Экранирование кода

```bsl
// BSLLS:CanonicalSpellingKeywordsInQuery-off
// BSLLS:CanonicalSpellingKeywordsInQuery-on
```

### Параметр конфигурационного файла

```json
"CanonicalSpellingKeywordsInQuery": false
```
