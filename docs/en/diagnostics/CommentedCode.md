# Commented out code

Type | Scope | Severity | Activated<br>by default | Minutes<br>to fix | Tags
--- | --- | --- | --- | --- | ---
`Code smell` | `BSL`<br>`OS` | `Minor` | `Yes` | `1` | `standard`<br>`badpractice`

## Parameters

Name | Type | Description | Default value
--- | --- | --- | ---
`threshold` | `float` | Порог чуствительности | `0.9F`

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

Software modules should not have commented out code fragments, as well as fragments,
which are in any way connected with the development process (debugging code, service marks, i.e. !!! _, MRG, etc.)
and with specific developers of this code.

For example, it is unacceptable to leave such fragments in the code after debugging or refactoring is completed:

```bsl
Procedure BeforeDelete(Failure)
	//If True Then
	//	Message("For debugging");
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

**ВНИМАНИЕ**:

Блок комментарием считается кодом, если хотя бы одна строка внутри блока определяется как код.

## Sources

- [Source](https://its.1c.ru/db/v8std/content/456/hdoc)
