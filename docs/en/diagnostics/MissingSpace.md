# Missing spaces to the left or right of operators `+ - * / = % < > <> <= >=`, keywords, and also to the right of `,` and `;` (MissingSpace)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

To improve code readability to the left and right of operators `+ - * / = % < > <> <= >=` there must be spaces. Also, the space should be to the right of `,` и `;`

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

The parameter has sense only if `,` is listed in one of three base parameters Defaults to `false`

If set to `false`

```bsl
    CommonModuleClientServer.MessageToUser(MessageText,,,, Cancel);        // Bad
    CommonModuleClientServer.MessageToUser(MessageText, , , , Cancel);     // Correct
```

If set to `true`

```bsl
    CommonModuleClientServer.MessageToUser(MessageText,,,, Cancel);        // Correct
CommonModuleClientServer.MessageToUser(MessageText, , , , Cancel);     // Correct
```
