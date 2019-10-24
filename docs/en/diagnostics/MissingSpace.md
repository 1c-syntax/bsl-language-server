# Missing spaces to the left or right of operators + - * / = % < > <> <= >=, and also to the right of , and ;

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Нет` | `1` | `badpractice` |


## <TODO PARAMS>

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
