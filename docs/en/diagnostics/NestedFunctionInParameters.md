# Initialization of method and constructor parameters by calling nested methods (NestedFunctionInParameters)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
<!-- Описание диагностики заполняется вручную. Необходимо понятным языком описать смысл и схему работу -->

Similarly, it is not recommended to use nested calls of other functions or other parameterized constructors when initializing constructor parameters  
.

At the same time, if the code with nested calls is compact (does not require the hyphenation of expressions) and is easy to read, then nested calls are acceptable.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

Incorrect:

```bsl
Attachments.Insert(  AttachedFile.Description,  New Picture(GetFromTempStorage(   AttachedFiles.GetFileData(AttachedFile.Ref).RefToFileBinaryData)));
```

It is correct to break such calls into separate operators using additional local variables:

```bsl
FileImageHRef = AttachedFiles.GetFileData(AttachedFile.Ref).RefToFileBinaryData; PictureData = New Picture(GetFromTempStorage(FileImageHRef)); Attachments.Insert(AttachedFile.Description, PictureData);
```

## Sources
<!-- Необходимо указывать ссылки на все источники, из которых почерпнута информация для создания диагностики -->


* [Parameters of procedures and functions (RU)](https://its.1c.ru/db/v8std#content:640:hdoc)
