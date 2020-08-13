# Missing spaces to the left or right of operators + - * / = % < > <> <= >=, and also to the right of , and ; (MissingSpace)

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
:-: | :-: | :-: | :-: | :-: | :-:
`Code smell` | `BSL`<br>`OS` | `Info` | `Yes` | `1` | `badpractice`

## Parameters

Name | Type | Description | Default value
:-: | :-: | :-- | :-:
`listForCheckLeft` | `String` | `List of symbols to check for the space to the left of (separated by space)` | ``````
`listForCheckRight` | `String` | `List of symbols to check for the space to the right of (separated by space)` | `, ;`
`listForCheckLeftAndRight` | `String` | `List of symbols to check for the space from both sides of (separated by space)` | `+ - * / = % < > <> <= >=`
`checkSpaceToRightOfUnary` | `Boolean` | `Check for space to the right of unary signs (+ -)` | `false`
`allowMultipleCommas` | `Boolean` | `Allow several commas in a row` | `false`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

Для улучшения читаемости кода слева и справа от операторов `+ - * / = % < > <> <= >=` должны быть пробелы. Так же пробел должен быть справа от `,` и `;`

## Examples

Incorrect

```bsl
Procedure Sum(Param1,Param2)
    If Param1=Param2 Then
        Sum=Price*Quantity;
    EndIf;
EndProcedure
```

Correct

```bsl
Процедура ВычислитьСумму(Параметр1, Параметр2)
    Если Параметр1 = Параметр2 Тогда
        Сумма = Цена * Количество;
    КонецЕсли;
КонецПроцедуры
```

### Using `checkSpaceToRightOfUnary` parameter

The parameter makes sense only in case the unary operator is listed in one of three base parameters.

If set to `false`

```bsl
А = -B;     // Correct
А = - B;    // Correct
```

If set to `true`

```bsl
А = -B;     // Incorrect
А = - B;    // Correct
```

### Using `allowMultipleCommas` parameter

The parameter has sense only if `,` is listed in one of three base parameters

If set to `false`

```bsl
CommonModuleClientServer.MessageToUser(MessageText,,,, Cancel);        // Incorrect
CommonModuleClientServer.MessageToUser(MessageText, , , , Cancel);     // Correct
```

If set to `true`

```bsl
CommonModuleClientServer.MessageToUser(MessageText,,,, Cancel);        // Correct
CommonModuleClientServer.MessageToUser(MessageText, , , , Cancel);     // Correct
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->

### Diagnostic ignorance in code

```bsl
// BSLLS:MissingSpace-off
// BSLLS:MissingSpace-on
```

### Parameter for config

```json
"MissingSpace": {
    "listForCheckLeft": "",
    "listForCheckRight": ", ;",
    "listForCheckLeftAndRight": "+ - * / = % < > <> <= >=",
    "checkSpaceToRightOfUnary": false,
    "allowMultipleCommas": false
}
```
