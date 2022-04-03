# Использование устаревших глобальных методов платформы 8.3.17 (DeprecatedMethods8317)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
В платформе `8.3.17` было реализовано свойство глобального контекста `ОбработкаОшибок` и 
стандартная функция `Управление настройками обработки ошибок`, позволяющая настроить тексты ошибок.

Методы глобального контекста считаются устаревшими:

* `КраткоеПредставлениеОшибки()`
* `ПодробноеПредставлениеОшибки()` 
* `ПоказатьИнформациюОбОшибке()`

Необходимо использовать одноименные методы объекта `ОбработкаОшибок`.

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* Источник: [Описание изменений платформы 8.3.17](https://dl03.1c.ru/content/Platform/8_3_17_1386/1cv8upd_8_3_17_1386.htm#27f2dc70-f0cf-11e9-8371-0050569f678a)