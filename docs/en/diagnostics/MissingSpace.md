# Missing spaces to the left or right of operators + - * / = % < > <> <= >=, and also to the right of , and ;

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Yes` | `1` | `badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `listForCheckLeft` | `String` | ```Symbols  for spaces from left side (space separated). Example: ) =``` | ```""``` |
| `listForCheckRight` | `String` | ```Symbols  for spaces from right side (space separated). Example: ( =``` | ```", ;"``` |
| `listForCheckLeftAndRight` | `String` | ```Symbols for spaces from both side(space separated) Example: + - * / = % < >``` | ```"+ - * / = % < > <> <= >="``` |
| `checkSpaceToRightOfUnary` | `Boolean` | ```Check space right from unary (+ -)``` | ```false``` |
| `allowMultipleCommas` | `Boolean` | ```Allow few commas``` | ```false``` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

To improve code readability to the left and right of operators `+ - * / = % < > <> <= >=` there must be spaces.
Also, the space should be to the right of `,` и `;`

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
Procedure Sum(Param1, Param2)
    If Param1 = Param2 Then
        Sum = Price * Quantity;
    EndIf;
EndProcedure
```

### Using `checkSpaceToRightOfUnary` parameter

The parameter makes sense only in case the unary operator if listed in one of three base parameters.

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
ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения,,,, Отказ);        // Incorrect
ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения, , , , Отказ);     // Correct
```

If set to `true`

```bsl
ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения,,,, Отказ);        // Correct
ОбщегоНазначенияКлиентСервер.СообщитьПользователю(ТекстСообщения, , , , Отказ);     // Correct
```
