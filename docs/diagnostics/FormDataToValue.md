# Использование метода ДанныеФормыВЗначение (FormDataToValue)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
В большинстве случаев, в модулях форм следует использовать метод формы РеквизитФормыВЗначение вместо метода ДанныеФормыВЗначение.

Рекомендация обусловлена соображениями унификации прикладного кода и тем, что синтаксис метода РеквизитФормыВЗначение проще, чем у ДанныеФормыВЗначение (а следовательно, меньше вероятность ошибки).
## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
```bsl
Процедура Тест()
    Форма=Док.ПолучитьФорму("ФормаДокумента");
    ДФ = Форма.ДанныеФормыВЗначение(Объект, Тип("ТаблицаЗначений"));
КонецПроцедуры
```
## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

Источник: [Использование РеквизитФормыВЗначение и ДанныеФормыВЗначение](https://its.1c.ru/db/v8std#content:409:hdoc)
