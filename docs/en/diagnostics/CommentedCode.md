# Commented out code (CommentedCode)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

Software modules should not have commented out code fragments, as well as fragments, which are in any way connected with the development process (debugging code, service marks, i.e. !!! _, MRG, etc.) and with specific developers of this code.

For example, it is unacceptable to leave such fragments in the code after debugging or refactoring is completed:

```bsl
Procedure BeforeDelete(Failure)
    //If True Then
    //  Message("For debugging");
    //EndIf;
EndProcedure
```
also wrong:
```bsl
Procedure BeforeDelete(Failure)
    If True Then
        // Ivanov: need fix
    EndIf;
EndProcedure
```

Correct: after debugging or refactoring is completed, remove the handler BeforeDelete from the code.

**ATTENTION**:  
A code block is considered commented, if at least one line inside the block is defined as code.

## Sources

* [Standard: Modules texts(RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
