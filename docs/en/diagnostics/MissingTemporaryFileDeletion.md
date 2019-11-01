# Missing temporary file deletion after using

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Major` | `Yes` | `5` | `badpractice`<br/>`standard` |

## Parameters 

| Name | Type | Description | Default value |
| :-: | :-: | :-- | :-: |
| `searchDeleteFileMethod` | `Pattern` | Ключевые слова поиска методов удаления / перемещения файлов | `"УдалитьФайлы|DeleteFiles|ПереместитьФайл|MoveFile"` |

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

## Examples

Incorrect:
```bsl
TempFileName = GetTempFileName("xml");
Data.Write(TempFileName);
// Not delete temporary file
```

Сorrect:
```bsl
TempFileName = GetTempFileName("xml");
Data.Write(TempFileName);

// Work with file
...

// Delete temporary file
Try
   DeleteFiles(TempFileName);
Catch
   WriteLogEvent(НСтр("ru = 'My subsystem.Action'"), EventLogLevel.Error, , , DetailErrorDescription(ErrorInfo()));
EndTry;
```
