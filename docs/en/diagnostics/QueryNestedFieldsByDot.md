# Getting objects nested fields data by dot in database query text (QueryNestedFieldsByDot)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Diagnostics allows you to control the dereference of reference fields through a dot in the 1C query language.
The purpose of this diagnostic is to prevent unnecessary implicit joins between tables.
and as a result, improve the performance of executing a database query.

## Examples
1. Base dereference through a dot (in temp. db or in select query)
   `ЗаказКлиентаТовары.Ссылка.Организация КАК Организация`
2. Dereference of fields in table join section
   `ВТ_РасчетыСКлиентами КАК ВТ_РасчетыСКлиентами
        ЛЕВОЕ СОЕДИНЕНИЕ ВТ_ДанныеЗаказовКлиента КАК ВТ_ДанныеЗаказовКлиента
        ПО ВТ_РасчетыСКлиентами.АналитикаУчетаПоПартнерам.Партнер = ВТ_ДанныеЗаказовКлиента.Партнер`
3. Dereference of fields in virtual tables
   `РегистрНакопления.РасчетыСКлиентами.Обороты(
               &НачалоПериода,
                   &КонецПериода,
                   ,
                   (АналитикаУчетаПоПартнерам.Партнер) В ...`
4. Dereference in cast function result fields
   `ВЫРАЗИТЬ(ВТ_ПланОтгрузок.ДокументПлан КАК Документ.ЗаказКлиента).Валюта.Наценка`
5. Dereference of fields in WHERE section
   `ГДЕ азКлиентаТовары.Ссылка.Дата МЕЖДУ &НачалоПериода И &КонецПериода`
## Sources
Source: [Dereference of composite type reference fields in the query language (RU)] (https://its.1c.ru/db/v8std/content/654/hdoc)
