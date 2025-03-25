# Methods compilation directive (CompilationDirectiveLost)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
All methods of managed forms and commands must have compilation directives.

## Examples

#### Incorrect:
```bsl
Procedure OnCreateAtServer()
...
EndProcedure 
```

#### Correct:
```bsl
&AtServer
Procedure OnCreateAtServer()
...
EndProcedure 
```

## Sources

* Helpful information: [Development of an interface for applied solutions on the 1C: Enterprise 8 platform (RU)](https://its.1c.ru/db/pubv8devui#content:189:1)
