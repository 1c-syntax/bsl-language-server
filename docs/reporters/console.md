# Console reporter

Ключ репортера - `console`

## Описание

Выводит результаты анализа в stdout и/или подключенные к логгеру. Результат каждого файла выводится
отдельной записью по мере его разбора — результат работы метода `toString()` объекта
[FileInfo](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/reporters/data/FileInfo.java).

Порядок файлов соответствует порядку завершения их разбора и от запуска к запуску может отличаться.

## Пример вывода

```log
Analysis date: 2019-01-28T15:32:06.856
FileInfo(path=Catalogs\МойСправочник\Ext\ManagerModule.bsl, mdoRef=Catalog.МойСправочник, diagnostics=[], metrics=...)
FileInfo(path=Catalogs\АккредитационныеКомиссии\Ext\ObjectModule.bsl, mdoRef=Catalog.АккредитационныеКомиссии, diagnostics=[Diagnostic [
  range = Range [
    start = Position [
      line = 55
      character = 0
    ]
    end = Position [
      line = 55
      character = 140
    ]
  ]
  severity = Information
  code = "LineLengthDiagnostic"
  source = "bsl-language-server"
  message = "Превышена длина строки"
  relatedInformation = null
]], metrics=...)
```
