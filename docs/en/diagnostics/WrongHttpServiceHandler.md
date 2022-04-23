# Missing handler for http service (WrongHttpServiceHandler)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
If there is no http-service method handler, then the call to the operation will return neither data, nor errors on the client side of the service, nor errors on the side of the service itself.

Important: the http service method should only accept one parameter.

The configurator notices violations only when the "Check for the existence of assigned handlers" flag is enabled.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Invalid handler method with empty body
```bsl
Function StorageGETRequest(Request)

EndFunction
```

Correct handler method - only one parameter is specified and there is a method body
```bsl
Function StorageGETRequest(Request)
    Return ModuleRequests.Get(Request);
EndFunction
```

Invalid handler method with the wrong number of parameters
```bsl
Function StorageGETRequest(Request, Additional)
    Return ModuleRequests.Get(Request);
EndFunction
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
* Useful information: [Refusal to use modal windows (RU)](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* [Developers guide 8.3.20. Internet service mechanisms (RU)](https://its.1c.ru/db/v8320doc#bookmark:dev:TI000000783)
* [Configuration guidelines. Web services and HTTP services (RU)](https://its.1c.ru/db/metod8dev/browse/13/-1/1989/2565/2567/2590)
