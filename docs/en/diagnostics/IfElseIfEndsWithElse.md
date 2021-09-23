# Else...The...ElseIf... statement should end with Else branch (IfElseIfEndsWithElse)

|     Type     |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |     Tags      |
|:------------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:-------------:|
| `Code smell` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |          `10`           | `badpractice` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The rule is applied whenever the conditional operator **If Then ElseIf** contains one or more blocks **ElseIf **. After block **ElseIf** must be followed by block **Else**.

The requirement to the final block**Else** - it protective programming. Such constructions are resistant to possible changes and do not mask possible errors.

The construct **Else** must either take appropriate action or contain a suitable comment as to why no action is being taken.


## Examples

Incorrect:

```bsl
If TypeOf(InputParameter) = Type("Structure") Then
    Result = FillByStructure(InputParameter);
ElsIf TypeOf(InputParameter) = Type("Document.Ref.MajorDocument") Then
    Result = FillByDocument(InputParameter);
EndIf;
```

Correct:

```bsl
If TypeOf(InputParameter) = Type("Structure") Then
    Result = FillByStructure(InputParameter);
ElsIf TypeOf(InputParameter) = Type("Document.Ref.MajorDocument") Then
    Result = FillByDocument(InputParameter);
Else
    Raise "Parameter of invalid type passed";
EndIf;
```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:IfElseIfEndsWithElse-off
// BSLLS:IfElseIfEndsWithElse-on
```

### Parameter for config

```json
"IfElseIfEndsWithElse": false
```
