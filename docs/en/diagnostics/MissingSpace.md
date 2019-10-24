# Missing spaces to the left or right of operators + - * / = % < > <> <= >=, and also to the right of , and ;

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL`<br/>`OS` | `Info` | `Нет` | `1` | `badpractice` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-: | :-: |
| `listForCheckLeft` | `String` | Список символов для проверки слева (разделенные пробелом). Например: ) = |  |
| `listForCheckRight` | `String` | Список символов для проверки справа (разделенные пробелом). Например: ( = |  |
| `listForCheckLeftAndRight` | `String` | Список символов для проверки с обоих сторон (разделенные пробелом). Например: + - * / = % < > |  |
| `checkSpaceToRightOfUnary` | `Boolean` | Проверять наличие пробела справа от унарных знаков (+ -) |  |
| `allowMultipleCommas` | `Boolean` | Разрешать несколько запятых подряд |  |

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
