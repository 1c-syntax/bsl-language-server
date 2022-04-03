# Пропущен постфикс "ВызовСервера" (CommonModuleNameServerCall)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Серверные общие модули для вызова с клиента содержат серверные процедуры и функции, доступные для использования 
из клиентского кода. Они составляют клиентский программный интерфейс сервера приложения.
Такие процедуры и функции размещаются в общих модулях с признаком:

* Сервер (флажок Вызов сервера установлен)

Серверные общие модули для вызова с клиента называются по общим правилам именования объектов метаданных
и должны именоваться с постфиксом "ВызовСервера" (англ. "ServerCall").

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

РаботаСФайламиВызовСервера, CommonServerCall

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


[Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:469:hdoc:2.2)