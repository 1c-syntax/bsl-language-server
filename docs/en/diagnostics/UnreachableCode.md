# Unreachable Code (UnreachableCode)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Code located after operators "Return", "GoTo", "Raise", "Break", "Continue" never will be executed.

Errors of unreachable code can be caused by developer carelessness when editing another's code.

The diagnostic also detects preprocessor branches that are impossible for every admissible combination of symbols. Enclosing conditions and preceding `#If / #ElsIf / #Else` alternatives are taken into account.

For BSL, the module contexts and the method's compilation directive are also considered. By default, `#If Server` and `#If Server And Client` branches are unreachable inside a client method of a managed form. Enabling the shared [`supportThickClientFileMode`](../features/ConfigurationFile.md) setting includes the file-mode thick client, where both `Client` and `Server` can be true, including inside a client form method. Unknown metadata does not restrict availability beyond the modes supported by the project. Context-selection settings of other diagnostics do not affect this check.

In BSL, assignments and returns excluded by compilation contexts do not contribute to inferred types. After a `#If Server And Not Server` branch assigning a number and an `#Else` branch assigning a string, the variable has only the `String` type. Repeating `#If Client` selects values from the earlier client branch, including values copied through intermediate variables.

For OneScript, symbols are independent and may be defined by the host application. `Windows` alone does not make a branch unreachable, whereas `Windows And Not Windows` excludes it for any definitions.

The whole excluded branch is highlighted, including method declarations, comments, and nested regions. Overlapping findings are merged. Diagnostics carry the LSP `Unnecessary` tag so that editors can dim unreachable code.

Incomplete or malformed preprocessor directives are not used to prove unreachability. A finding is emitted only when the condition has been proved impossible.

## Examples

The following branch is never compiled:

```bsl
#If Server And Not Server Then
Procedure Unavailable()
    Message("This code is excluded");
EndProcedure
#EndIf
```

```bsl
Procedure Example()
    Return;
    // Code below operator Return will never be executed
    For each Line from Lines Do
        If Condition2 Then
            Method();
        EndIf;
    EndDo;
EndProcedure
```

```bsl
Function Example(Parameter1, Parameter2)
    If Error Then
        Raise "Error occurred";
        // After rise exception the code bellow will be ignored
        Parameter1 = Parameter2;
    EndIf; 
    Return Parameter1;
EndFunction
```
