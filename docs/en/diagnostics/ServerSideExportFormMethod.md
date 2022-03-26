# Server-side export form method (ServerSideExportFormMethod)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Описание диагностики
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

In a form module, you can declare export methods that are available in the client context (usually, these are form notification event handlers). For export methods of the form, only the compilation directive `AtClient` can be specified, since for the rest there is no practical sense: accessing form methods from outside is available only after calling the method `GetForm`, which is available only on the client.

Specifying a different compilation directive for the export method or its absence is considered an error.

*In some versions of the 1C:Enterprise platform, there was an error that allowed using export server-side methods of forms, but it is unacceptable to design a solution using undocumented platform capabilities.*

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect use of export methods on a form

```bsl
Procedure One() Export
  // procedure without directive is available on the server
EndProcedure

&AtServerNoContext
Procedure AtServerNoContext() Export
EndProcedure

&AtServer
Procedure AtServer() Export
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->

* [Development of the interface for applied solutions on the "1C:Enterprise" platform (RU). Ch 3.5. Execution of the form module on the client and on the server](https://its.1c.ru/db/pubv8devui/content/191/hdoc)
