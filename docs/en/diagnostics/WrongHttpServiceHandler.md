# Missing handler for http service (WrongHttpServiceHandler)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |             Tags              |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:-----------------------------:|
| `Error` |             `BSL`             | `Critical` |              `Yes`              |                `10`                 |    `suspicious`<br>`error`    |

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

* `suspicious`<br>`error`
* <!-- Блоки выше заполняются автоматически, не трогать -->
* Diagnostics description
* <!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In the absence of a http service operation handler, a call to this method will not give out either the data itself, or an error on the client side of the service, or an error on the side of the service itself.
* It is important to remember that the http service method should only accept one parameter.

## The configurator notices violations of the specified restrictions only when the "Check for the existence of assigned handlers" flag is enabled.

Examples
### <!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Invalid handler method with empty body

```bsl
Function StorageGETRequest(Request)

EndFunction
```

### Correct handler method - only one parameter is specified and there is a method body

```bsl
Function StorageGETRequest(Request)
    Return Requests.Getter(Request);
EndFunction
```
