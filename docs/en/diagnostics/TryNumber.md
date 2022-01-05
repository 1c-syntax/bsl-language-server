# Cast to number of try catch block (TryNumber)

|      Type      |    Scope    | Severity |    Activated<br>by default    |    Minutes<br>to fix    |    Tags    |
|:-------------:|:-----------------------------:|:--------:|:------------------------------:|:-----------------------------------:|:----------:|
| `Code smell` |         `BSL`<br>`OS`         | `Major` |              `Yes`              |                 `2`                 | `standard` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

It is incorrect to use exceptions to cast a value to a type. For such operations, it is necessary to use the capabilities of the TypeDescription object.

## Examples

Incorrect:

```bsl
Try
 NumberDaysAllowance = Number(Value);
Raise
 NumberDaysAllowance = 0; // default value
EndTry;
```

Correct:

```bsl
TypeDescription = New TypeDescription("Number");
NumberDaysAllowance = TypeDescription.CastValue(Value);
```

## Sources

* [Catching Exceptions in Code (RU)](https://its.1c.ru/db/v8std#content:499:hdoc)

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:TryNumber-off
// BSLLS:TryNumber-on
```

### Parameter for config

```json
"TryNumber": false
```
