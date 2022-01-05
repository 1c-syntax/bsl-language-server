# No NULL checks for fields from joined tables (FieldsFromJoinsWithoutIsNull)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |                         Tags                         |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:----------------------------------------------------:|
| `Error` |         `BSL`<br>`OS`         | `Critical` |              `Yes`              |                 `2`                 |       `sql`<br>`suspicious`<br>`unpredictable`       |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Diagnostics checks fields from left, right, full joins that are not checked with `ISNULL()` or `IS NOT NULL`  or `NOT IS NULL` .

В запросах нельзя использовать реквизиты из присоединяемых слева или справа таблиц без проверки значений на `NULL`. 
Указанное обращение может приводить к ошибкам, если условие соединения не выполнено и нет подходящих записей в левой или правой таблице.
В итоге в результате запроса можно получить неожиданные данные и система может повести себя неверным образом.

Важно помнить, что любое сравнение значения `NULL` с любым другими выражением всегда ложно, даже сравнение `NULL` и `NULL` всегда ложно. 
Смотрите ниже пример подобных неверных сравнений.
Поэтому нужно правильно выполнять сравнение с `NULL` - или через оператор `ЕСТЬ NULL` или через функцию `ЕСТЬNULL()`.

Severity

Activated by default

Minutes<br> to fix

## Tags
`Error`
`BSL`<br>`OS`

`Critical`
`Yes`
Correct
`sql`<br>`suspicious`<br>`unpredictable`
Also correct:
Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Diagnostics checks fields from left, right, full joins that are not validated with `ISNULL()` or `NOT IS NULL` or `IS NOT NULL</0 >.</p>
<p spaces-before="0">Queries cannot use attributes from left-join or right-join tables without checking the values for <code>NULL`. Such a call can lead to errors if the join condition is not met and there are no matching records in the left or right table. As a result, as a result of executing the query, you may receive unexpected data and the system may behave in an incorrect way.
It is important to remember that any comparison of the value `NULL` with any other expression is always false, even the comparison of `NULL` and `NULL` is always false. The following are examples of such incorrect comparisons. It is correct to compare with `NULL` - operator `IS NULL` or function `ISNULL()`.

## Left \ right joins are often used, although the data allows an inner join without checking for `NULL`.
Additional checks of field values can be performed in the 1C code, and not in the query text. This makes it difficult to read the code and refactor the code, because the context of the access to the field has to be considered in several places. It should be remembered that simple checks in a query are performed a little faster and easier than in interpreted 1C code.

* These problems are the most common mistakes made by 1C developers of all skill levels.
* Examples
* <!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Example showing NULL comparison problems - joining 2 tables incorrectly and showing different comparison methods
* ```sdbl
ВЫБРАТЬ
  ВЫБОР
    КОГДА Левая.Поле2 = 0 ТОГДА "Равно 0 - не работает"
    КОГДА Левая.Поле2 <> 0 ТОГДА "НЕ Равно 0 - не работает"
    КОГДА Левая.Поле2 = NULL ТОГДА "Равно NULL - не работает"
    КОГДА Левая.Поле2 ЕСТЬ NULL ТОГДА "ЕСТЬ NULL - этот вариант работает"
    КОГДА ЕСТЬNULL(Левая.Поле2, 0) = 0  ТОГДА "ЕСТЬNULL() - этот вариант также работает"
    ИНАЧЕ "Иначе"
  КОНЕЦ
ИЗ
  Первая КАК Первая
  ЛЕВОЕ СОЕДИНЕНИЕ Левая КАК Левая
  ПО Ложь // чтобы не было соединения
```
* Подозрительный код обращения к реквизиту присоединенной таблицы
    * ```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  РегистрПродажи.Сумма КАК Сумма // здесь ошибка
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
```
* Right
* ```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  ЕстьNULL(РегистрПродажи.Сумма, 0) КАК Сумма
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
```
* Also correct
    * ```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  ВЫБОР КОГДА РегистрПродажи.Сумма Есть NULL ТОГДА 0
  ИНАЧЕ  РегистрПродажи.Сумма 
  КОНЕЦ КАК Сумма
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
```
* И еще возможный вариант

## ```sdbl
ВЫБРАТЬ 
  ДокументыПродажи.Ссылка КАК ДокПродажи,
  РегистрПродажи.Сумма КАК Сумма
ИЗ Документ.РеализацияТоваровУслуг КАК ДокументыПродажи
ЛЕВОЕ СОЕДИНЕНИЕ  РегистрНакопления.Продажи КАК РегистрПродажи
ПО ДокументыПродажи.Ссылка = РегистрПродажи.Документ
ГДЕ
    РегистрПродажи.Документ ЕСТЬ НЕ NULL
    //или НЕ РегистрПродажи.Документ ЕСТЬ NULL
```

Последний вариант - не самый лучший, т.к. в нем фактически эмулируется внутреннее соединение. И проще явно указать `ВНУТРЕННЕЕ СОЕДИНЕНИЕ` вместо использования левого соединения с проверкой `ЕСТЬ НЕ NULL` или `НЕ ЕСТЬ NULL`
### Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

### Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)

Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
