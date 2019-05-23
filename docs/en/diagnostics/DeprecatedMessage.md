# Restriction on using of the obsolete method "Message"

Для вывода сообщений пользователю во всех случаях следует использовать объект СообщениеПользователю, даже когда сообщение не «привязывается» к некоторому элементу управления формы. Метод Сообщить применять не следует.

*When used the Standard Subsystems Library it is recommended use procedure MessageUser from common module CommonPurposeClientServer, which use object UserMessage.*

Источник: [Стандарт: Ограничение на использование метода Сообщить](https://its.1c.ru/db/v8std#content:418:hdoc)
