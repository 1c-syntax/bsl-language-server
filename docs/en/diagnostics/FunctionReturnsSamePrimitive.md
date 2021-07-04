# The function always returns the same primitive value (FunctionReturnsSamePrimitive)

|  Type   |        Scope        | Severity | Activated<br>by default | Minutes<br>to fix |              Tags               |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:-------------------------------:|
| `Error` | `BSL`<br>`OS` | `Major`  |             `Yes`             |           `5`           | `design`<br>`badpractice` |

## Parameters


|           Name           |   Type    |         Description          | Default value |
|:------------------------:|:---------:|:----------------------------:|:-------------:|
|     `skipAttachable`     | `Boolean` | `Ignore attachable methods`  |    `true`     |
| `caseSensitiveForString` | `Boolean` | `Case sensitive for strings` |    `false`    |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

A function should not return the same primitive value. If the result of the function isn't use into code, then you need the function rewrite to the procedure.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Bad:
```bsl
Function CheckString(Val RowTable)

    If ItsGoodString(RowTable) Then
        ActionGood();
        Return True;
    ElsIf ItsNodBadString(RowTable) Then
        ActionNoBad();
        Return True;
     Else
        Return True;
    EndIf;

EndFunction
```

Good:
```bsl
Function CheckString(Val RowTable)

    If ItsGoodString(RowTable) Then
        ActionGood();
    ElsIf ItsNodBadString(RowTable) Then
        ActionNoBad();
    Else
        ActionElse();
    EndIf;

EndFunction
```

## Nuances

Attachable functions excluded from the scan. Example:
```bsl
Function Attachable_RandomAction(Command)

    If ValueIsFilled(CurrentDate) Then
        Return Undefined;
    EndIf;

    Return Undefined;

EndFunction
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:FunctionReturnsSamePrimitive-off
// BSLLS:FunctionReturnsSamePrimitive-on
```

### Parameter for config

```json
"FunctionReturnsSamePrimitive": {
    "skipAttachable": true,
    "caseSensitiveForString": false
}
```
