# Wrong handler for web service (WrongWebServiceHandler)

|   Type    |    Scope    |  Severity   |    Activated<br>by default    |    Minutes<br>to fix    |             Tags              |
|:--------:|:-----------------------------:|:-----------:|:------------------------------:|:-----------------------------------:|:-----------------------------:|
| `Error` |             `BSL`             | `Critical` |              `Yes`              |                `10`                 |    `suspicious`<br>`error`    |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
Wrong handler for web service (WrongWebServiceHandler)

Type

Scope

## Severity
Activated by default
Minutes<br> to fix

Tags
`Error`

`BSL`
`Critical`

## `Yes`
`10`

* `suspicious`<br>`error`
* <!-- Блоки выше заполняются автоматически, не трогать -->
* Diagnostics description
* <!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
In the absence of a web service operation handler, a call to this operation will not give out either the data itself, or an error on the client side of the service, or an error on the side of the service itself.
* It is important to remember that the number of parameters for a web service operation must match the number of parameters specified in the settings for a web service operation.

## The configurator notices violations of the specified restrictions only when the "Check for the existence of assigned handlers" flag is enabled.

Examples
### <!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->
Invalid handler method with empty body

```bsl
Function FillCatalogs(MobileDeviceID, MessageExchange)

EndFunction
```

### Correct handler method - there is a method body and the correct set of parameters is specified

```bsl
Function FillCatalogs(MobileDeviceID, MessageExchange)
    Return Mobiles.LoadCatalogs(MobileDeviceID, MessageExchange);
EndFunction
```
