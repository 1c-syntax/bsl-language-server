# Server calls in form events (ServerCallsInFormEvents)

<!-- Блоки выше заполняются автоматически, не трогать -->

## Description

<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Events `OnActivateRow` and `OnStartChoice` should not contain server procedure calls. These events should only execute on the client.

Only server calls to methods of this form are diagnosed; calling MyModuleServer.MyServerMethod will not result in an error.

According to the [Infostart article](https://infostart.ru/1c/articles/1225834/), calling server procedures from these events can lead to performance issues and form behavior problems.

## Examples

<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

### Incorrect

```bsl
&AtClient
Procedure OnActivateRow(Element, SelectedRow, Field, NewValue, StandardProcessing)
    // Error: server procedure call from client event
    TableFormOnActivateRowAtServer();
    StandardProcessing = False;
EndProcedure

&AtServer
Procedure TableFormOnActivateRowAtServer()
    RaiseException "test";
EndProcedure
```

### Correct

```bsl
Procedure OnActivateRow(Element, SelectedRow, Field, NewValue, StandardProcessing)
    // Correct: only client-side processing
    StandardProcessing = False;
EndProcedure
```

## Sources

<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->

- [GitHub Issue #3473](https://github.com/1c-syntax/bsl-language-server/issues/3473)
- [Infostart: Server calls that should not be called](https://infostart.ru/1c/articles/1225834/)
