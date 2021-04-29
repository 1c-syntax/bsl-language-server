# Console reporter

Reporter option - `console`

## Description

Output the analize result in stdout and/or attached logger. Output result, returned `toString()` method of object [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/master/src/main/java/org/github/_1c_syntax/bsl/languageserver/diagnostics/reporter/AnalysisInfo.java)

## Sample output

```log
Analysis date: 2019-01-28T15:32:06.856
[FileInfo(path=C:\src\cf\Catalogs\MyCatalog\Ext\ManagerModule.bsl, diagnostics=[]), FileInfo(path=C:\src\cf\Catalogs\Goods\Ext\ObjectModule.bsl, diagnostics=[Diagnostic [
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
  message = "Line length exceeded"
  relatedInformation = null
]])]
```
