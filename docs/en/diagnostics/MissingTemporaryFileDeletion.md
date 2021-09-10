# Missing temporary file deletion after using (MissingTemporaryFileDeletion)

|  Type   |        Scope        | Severity |    Activated<br>by default    |    Minutes<br>to fix    |               Tags                |
|:-------:|:-------------------:|:--------:|:-----------------------------:|:-----------------------:|:---------------------------------:|
| `Error` |    `BSL`<br>`OS`    | `Major`  |             `Yes`             |           `5`           |    `badpractice`<br>`standard`    |

## Parameters


|           Name           |   Type   |                    Description                     |                                        Default value                                        |
|:------------------------:|:--------:|:--------------------------------------------------:|:-------------------------------------------------------------------------------------------:|
| `searchDeleteFileMethod` | `String` | `Keywords to search for delete/move files methods` | `УдалитьФайлы|DeleteFiles|НачатьУдалениеФайлов|BeginDeletingFiles|ПереместитьФайл|MoveFile` |
<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

After you finished working with temporary file or folder, you need to delete it yourself. You should not rely on automatic deletion of files and folders before platform start. This can cause temp folder free space shortage.

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

## Особенности работы

Диагностика определяет корректность работы с временными файлами по наличию методов удаления либо перемещения.

Если в прикладном решении используется свой метод удаления/перемещения поверх платформенного, то стоит указать его в параметре диагностики, добавив через `|`. Диагностика понимает как глобальные методы, так и расположенные в общих модулях или модулях менеджеров.

Ниже приведены примеры настройки

- Глобальный метод `МоеУдалениеФайлов` в модуле `ГлобальныйСервер` в параметре указывается как `МоеУдалениеФайлов`
- Метод `МоеУдалениеФайлов` в общем модуле `ФайлыКлиентСервер` в параметре указывается как `ФайлыКлиентСервер.МоеУдалениеФайлов`
- Метод `МоеУдалениеФайлов` в модуле менеджера справочника `ФайловыеОперации` в параметре указывается как `Справочники.ФайловыеОперации.МоеУдалениеФайлов`

и так далее.

## Источники

* [Доступ к файловой системе из кода конфигурации](https://its.1c.ru/db/v8std#content:542:hdoc)

## Сниппеты

<!-- Блоки ниже заполняются автоматически, не трогать -->
### Diagnostic ignorance in code

```bsl
// BSLLS:MissingTemporaryFileDeletion-off
// BSLLS:MissingTemporaryFileDeletion-on
```

### Parameter for config

```json
"MissingTemporaryFileDeletion": {
    "searchDeleteFileMethod": "УдалитьФайлы|DeleteFiles|НачатьУдалениеФайлов|BeginDeletingFiles|ПереместитьФайл|MoveFile"
}
```
