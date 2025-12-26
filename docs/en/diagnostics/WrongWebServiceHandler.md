# Wrong handler for web service (WrongWebServiceHandler)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Diagnostics description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In the absence of a web service operation handler, a call to this operation will not give out either the data itself, or an error on the client side of the service, or an error on the side of the service itself.

It is important to remember: the number of parameters of the web service operation method must match the number of parameters specified in the settings of the web service operation.

The configurator notices violations of the specified restrictions only when the "Check for the existence of assigned handlers" flag is enabled.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Invalid handler method with empty body
```bsl
Function FillCatalogs(MobileDeviceID, MessageExchange)

EndFunction
```

Correct handler method - there is a method body and the correct set of parameters is specified
```bsl
Function FillCatalogs(MobileDeviceID, MessageExchange)
    Return Mobiles.LoadCatalogs(MobileDeviceID, MessageExchange);
EndFunction
```

Invalid handler method with the wrong number of parameters
```bsl
Function FillCatalogs(MobileDeviceID, MessageExchange, Additionals)
    Return Mobiles.LoadCatlogs(MobileDeviceID, MessageExchange);
EndFunction
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->
<!-- Примеры источников

* Источник: [Стандарт: Тексты модулей](https://its.1c.ru/db/v8std#content:456:hdoc)
* Полезная информация: [Отказ от использования модальных окон](https://its.1c.ru/db/metod8dev#content:5272:hdoc)
* Источник: [Cognitive complexity, ver. 1.4](https://www.sonarsource.com/docs/CognitiveComplexity.pdf) -->
* [Developer Guide 8.3.20 - Web service mechanisms (RU)](https://its.1c.ru/db/v8320doc#bookmark:dev:TI000000783)
* [Web-services and HTTP-services - recommendations from 1C](https://its.1c.ru/db/metod8dev/browse/13/-1/1989/2565/2567/2590)
