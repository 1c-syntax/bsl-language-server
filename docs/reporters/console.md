# Console reporter

Ключ репортера - `console`

## Описание

Выводит результаты анализа в stdout и/или подключенные к логгеру. Выводится результат работы метода `toString()` объекта [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/master/src/main/java/org/github/_1c_syntax/bsl/languageserver/diagnostics/reporter/AnalysisInfo.java)

## Пример вывода

```log
Analysis date: 2019-01-28T15:32:06.856
[FileInfo(path=C:\src\cf\Catalogs\МойСправочник\Ext\ManagerModule.bsl, diagnostics=[]), FileInfo(path=C:\src\cf\Catalogs\АккредитационныеКомиссии\Ext\ObjectModule.bsl, diagnostics=[Diagnostic [
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
]])]
```
