# There is no check for the attribute DataExchange.Load in the object's event handler (DataExchangeLoading)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL` | `Critical` | `Yes` | `5` | `standard`<br/>`badpractice`<br/>`unpredictable` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `findFirst` | `Boolean` | ```Check should go first``` | ```false``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad:
```bsl
Procedure BeforeWrite(Cancel)

    If Not Cancel Then
        RandomAlgorithm();
    EndIf;

    // code handler
    //
    // ...
    
EndProcedure
```

Good:
```bsl
Procedure BeforeWrite(Cancel)
    If DataExchange.Load Then
         Return;
    EndIf;

    // code handler
    // ...
EndProcedure
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Стандарт: Использование признака ОбменДанными.Загрузка в обработчиках событий объекта](https://its.1c.ru/db/v8std#content:773)
* [Обработчик события ПриЗаписи](https://its.1c.ru/db/v8std#content:465)
* [Обработчик события ПередЗаписью](https://its.1c.ru/db/v8std#content:464)
* [Обработчик события ПередУдалением](https://its.1c.ru/db/v8std#content:752)

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
