# Using of the deprecated method "Find" (DeprecatedFind)

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Minor` | `Yes` | `2` | `deprecated` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Method "Find" is deprecated. Use "StrFind" instead.

## Examples

### Noncompliant

```bsl
If Find(Collaborator.Name, "Boris") > 0 Then
    
EndIf; 
```


### Compliant

```bsl
If StrFind(Collaborator.Name, "Boris") > 0 Then
    
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
