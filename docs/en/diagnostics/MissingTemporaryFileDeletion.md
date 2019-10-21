# Missing temporary file deletion after using

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
