# Использование возможностей выполнения внешнего кода (UsingExternalCodeTools)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Для прикладных решений запрещено выполнение в небезопасном режиме любого кода на сервере 1С:Предприятия, который не является частью самого прикладного решения (конфигурации).  
Ограничение не распространяется на код, прошедший аудит, и на код, выполняемый на клиенте.

Примеры недопустимого выполнения «внешнего» кода в небезопасном режиме:

* внешние отчеты и обработки (печатные формы и т.п.)
* расширения конфигурации

### Ограничения

На данный момент контекст сервера не анализируется, т.е. диагностика срабатывает как в клиентском, так и в серверном контекстах

## Примеры
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Источники
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Ограничение на выполнение «внешнего» кода](https://its.1c.ru/db/v8std#content:669:hdoc)