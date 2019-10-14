# Commented out code

Software modules should not have commented out code fragments, as well as fragments,
which are in any way connected with the development process (debugging code, service marks, i.e. !!! _, MRG, etc.)
and with specific developers of this code.

For example, it is unacceptable to leave such fragments in the code after debugging or refactoring is completed:

```bls
Procedure BeforeDelete(Failure)
	//If True Then
	//	Message("For debugging");
	//EndIf;
EndProcedure
```

also wrong:

```bls
Procedure BeforeDelete(Failure)
	If True Then
		// Ivanov: need fix
	EndIf;
EndProcedure
```

Correct: after debugging or refactoring is completed, remove the handler BeforeDelete from the code.

[Source](https://its.1c.ru/db/v8std/content/456/hdoc)

**Parameters** :

- *commentedCodeThreshold* - sensitivity threshold above the value of which commented text is considered a code.It is indicated in the range from 0 to 1. The value for each commented section is filled in by the key words in the text.

**ATTENTION** : 

A code block is considered commented , if at least one line inside the block is defined as code.
