# Using of the deprecated method "Find"

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Code smell` | `BSL` | `Minor` | `No` | `2` | `deprecated` |

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

