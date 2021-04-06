# There is no check for the attribute DataExchange.Load in the object's event handler (DataExchangeLoading)

 |  Type   | Scope |  Severity  | Activated<br>by default | Minutes<br>to fix |                            Tags                            |
 |:-------:|:-----:|:----------:|:-----------------------------:|:-----------------------:|:----------------------------------------------------------:|
 | `Error` | `BSL` | `Critical` |             `Yes`             |           `5`           | `standard`<br>`badpractice`<br>`unpredictable` |

## Parameters

 |    Name     |   Type    | Description             | Default value |
 |:-----------:|:---------:|:----------------------- |:-------------:|
 | `findFirst` | `Boolean` | `Check should go first` |    `false`    | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
All actions in the event-handler procedures BeforeWrite, OnWrite, BeforeDelete should be performed after checking for DataExchange.Load.

This is necessary so that no business logic of the object is executed when writing the object through the data exchange mechanism, since it has already been executed for the object in the node where it was created. В этом случае все данные загружаются в ИБ "как есть", без искажений (изменений), проверок или каких-либо других дополнительных действий, препятствующих загрузке данных.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad:
```bsl
Procedure BeforeWrite(Cancel)     If DataExchange.Load Then          Return;     EndIf;      // code handler     // ...

EndProcedure
```
Good:
```bsl
Procedure BeforeWrite(Cancel)      If Not Cancel Then         RandomAlgorithm();     EndIf;      // code handler     //     // ...      EndProcedure
КонецПроцедуры
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

* [Standard: Using DataExchange.Load in object handlers (RU)](https://its.1c.ru/db/v8std#content:773)
* [Handler OnWrite (RU)](https://its.1c.ru/db/v8std#content:465)
* [Handler BeforeWrite (RU)](https://its.1c.ru/db/v8std#content:464)
* [Handler BeforeDelete (RU)](https://its.1c.ru/db/v8std#content:752)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DataExchangeLoading-off
// BSLLS:DataExchangeLoading-on
```

### Parameter for config

```json
"DataExchangeLoading": {
    "findFirst": false
}
```
