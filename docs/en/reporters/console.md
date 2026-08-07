# Console reporter

Reporter option - `console`

## Description

Output the analysis result in stdout and/or attached logger. Each file is reported as a separate
record as soon as it has been analyzed - the output is the result of the `toString()` method of the
[FileInfo](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/reporters/data/FileInfo.java) object.

Files appear in the order their analysis completes, which may differ between runs.

## Sample output

```log
Analysis date: 2019-01-28T15:32:06.856
FileInfo(path=Catalogs\MyCatalog\Ext\ManagerModule.bsl, mdoRef=Catalog.MyCatalog, diagnostics=[], metrics=...)
FileInfo(path=Catalogs\Goods\Ext\ObjectModule.bsl, mdoRef=Catalog.Goods, diagnostics=[Diagnostic [
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
]], metrics=...)
```
