# Ограничение на использование устаревшего метода "Сообщить" (DeprecatedMessage)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики

Для вывода сообщений пользователю во всех случаях следует использовать объект СообщениеПользователю, даже когда сообщение не «привязывается» к некоторому элементу управления формы. Метод Сообщить применять не следует.

*При использовании в конфигурации Библиотеки стандартных подсистем рекомендуется использовать процедуру СообщитьПользователю общего модуля ОбщегоНазначенияКлиентСервер, которая работает с объектом СообщениеПользователю.*

## Источники

* [Стандарт: Ограничение на использование метода Сообщить](https://its.1c.ru/db/v8std#content:418:hdoc)
