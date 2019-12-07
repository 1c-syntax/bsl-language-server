# Missing code in Raise block in "Try ... Raise ... EndTry" (MissingCodeTryCatchEx)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Major` | `Yes` | `15` | `standard`<br/>`badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `commentAsCode` | `Boolean` | ```Comment as code``` | ```false``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is unacceptable to catch any exceptions without a trace for the system administrator

*Wrong*

```bsl
Попытка 
    // код, приводящий к вызову исключения
    ....
Исключение // перехват любых исключений
КонецПопытки;

```

As a rule, such a design hides a real problem, which is subsequently impossible to diagnose.

*Right*

```bsl
Попытка 
    // код, приводящий к вызову исключения
    ....
Исключение
    // Пояснение причин перехвата всех исключений "незаметно" от пользователя.
    // ....
    // И запись события в журнал регистрации для системного администратора.
    ЗаписьЖурналаРегистрации(НСтр("ru = 'Выполнение операции'"),
       УровеньЖурналаРегистрации.Ошибка,,,
       ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
КонецПопытки;
```

## Sources

* [Catching Exceptions in Code (RU)](https://its.1c.ru/db/v8std#content:499:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MissingCodeTryCatchEx-off
// BSLLS:MissingCodeTryCatchEx-on
```

### Parameter for config

```json
"MissingCodeTryCatchEx": {
    "commentAsCode": false
}
```
