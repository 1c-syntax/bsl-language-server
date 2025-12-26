# Missing temporary file deletion after using (MissingTemporaryFileDeletion)

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

## Peculiarities

Diagnostics determines the correctness of working with temporary files by the presence of methods for deleting or moving.

If the applied solution uses its own method of removing/moving over the platform one, then it is worth specifying it in the diagnostic parameter, adding it after `|`. Diagnostics understands both global methods and those located in common modules or manager modules.

The following is an examples of a settings

- The global method `MyFileDeletion` in the `GlobalServer` module in the parameter is specified as `MyFileDeletion`
- Method `MyFileDeletion` in the common module `FilesClientServer` in the parameter is specified as `FilesClientServer.MyFileDelete`
- Method `MyFileOperations` in the module of the catalog manager `FileOperations` in the parameter is specified as `Catalogs.FileOperations.MyFileOperations`

and so on.

## Sources

* [File system access from application code](https://its.1c.ru/db/v8std#content:542:hdoc)
