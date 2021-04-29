# Using of the deprecated method "Find" (DeprecatedFind)

 |     Type     | Scope | Severity | Activated<br>by default | Minutes<br>to fix |     Tags     |
 |:------------:|:-----:|:--------:|:-----------------------------:|:-----------------------:|:------------:|
 | `Code smell` | `BSL` | `Minor`  |             `Yes`             |           `2`           | `deprecated` | 

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Method "Find" is deprecated. Use "StrFind" instead.

## Examples

Incorrect:

```bsl

If Find(Employee.Name, "Boris") > 0 Then

EndIf; 

```


Correct:

```bsl

If StrFind(Employee.Name, "Boris") > 0 Then

EndIf; 

```

## Snippets

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:DeprecatedFind-off
// BSLLS:DeprecatedFind-on
```

### Parameter for config

```json
"DeprecatedFind": false
```
