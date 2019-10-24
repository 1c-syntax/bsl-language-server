# Missing temporary file deletion after using

| Type | Scope | Severity | Activated<br/>by default | Minutes<br/>to fix | Tags |
| :-: | :-: | :-: | :-: | :-: | :-: |
| `Error` | `BSL`<br/>`OS` | `Major` | `Нет` | `5` | `badpractice`<br/>`standard` |


## TODO PARAMS

## Description

# Missing temporary file deletion after using

## Parameters

* `searchDeleteFileMethod` - `Строка` - Keywords to search methods for move or delete files. 
Default ``УдалитьФайлы|DeleteFiles|ПереместитьФайл|MoveFile``.

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
