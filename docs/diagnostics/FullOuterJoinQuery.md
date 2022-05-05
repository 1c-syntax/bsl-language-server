# Использование конструкции "ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ" в запросах (FullOuterJoinQuery)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
Следует избегать использования конструкции ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ в запросах, особенно когда в качестве СУБД используется PostgreSQL. В тех случаях, когда это возможно, необходимо переписать запрос без использования ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ.
## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Запрос из примера ниже приведет к повышенной нагрузке при использовании PostgreSQL.
```bsl
Процедура Тест1()

    Запрос = Новый Запрос;
    Запрос.Текст = "ВЫБРАТЬ
                   |    Товары.Номенклатура КАК Номенклатура,
                   |    ЕСТЬNULL(ПланПродаж.Сумма, 0) КАК СуммаПлан,
                   |    ЕСТЬNULL(ФактическиеПродажи.Сумма, 0) КАК СуммаФакт
                   |ИЗ
                   |    Товары КАК Товары
                   |        ЛЕВОЕ СОЕДИНЕНИЕ ПланПродаж КАК ПланПродаж
                   |            ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ ФактическиеПродажи КАК ФактическиеПродажи // Диагностика должна сработать здесь
                   |            ПО ПланПродаж.Номенклатура = ФактическиеПродажи.Номенклатура
                   |        ПО Товары.Номенклатура = ПланПродаж.Номенклатура";

КонецПроцедуры
```
## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* [Ограничение на использование конструкции "ПОЛНОЕ ВНЕШНЕЕ СОЕДИНЕНИЕ" в запросах](https://its.1c.ru/db/v8std#content:435:hdoc)
* [Руководство администратора, особенности использования PostgreSQL](https://its.1c.ru/db/metod8dev#content:1556:hdoc)