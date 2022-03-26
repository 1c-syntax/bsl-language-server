# TempFilesDir() method call (TempFilesDir)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->
When you use GetTemporaryFileName(), 1С:Enterprise retains control over these files and by default deletes them as soon as a working process (if a file is created on the server side) or client application (if a file is created on the client side) is restarted.

If a temporary file name is generated otherwise, and the application code fails (or is for any other reason unable) to delete a temporary file, it is not controlled by the platform and is saved in the file system for an indefinite time. Lost temporary files accumulated in the system can pose a serious problem, specifically for infobases with a great number of active users (for example, in the service mode).
## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:

```bsl
Catalog = TempFilesDir();
FileName = String(New UUID) + ".xml";
TempFile = Catalog + FileName;
Data.Write(TempFile);
```

Correct:

```bsl
TempFile = GetTempFileName("xml");
Data.Write(TempFile);
```

To create a temporary directory, it is recommended to use the one obtained by the GetTempFileName method (with the exception of the web client).

Incorrect:

```bsl
ArchFile = New ZipFileReader(FileName);
ArchCatalog = TempFilesDir()+"main_zip\";
CreateDirectory(ArchCatalog);
ArchFile.ExtractAll(ArchCatalog);
```

Correct:

```bsl
ArchFile = New ZipFileReader(FileName);
ArchCatalog = GetTempFileName() + "\main_zip\";
CreateDirectory(ArchCatalog);
ArchFile.ExtractAll(ArchCatalog);
```

## Sources

* Source: [Standard: Temporary Files and Directories](https://its.1c.ru/db/v8std#content:542:hdoc)
