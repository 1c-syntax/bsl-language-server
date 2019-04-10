# JSON reporter

Reporter option - `json`

## Description

Output the analize result to file  `bsl-json.json` in the current workspace directory. Output the result of JSON serialization [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/master/src/main/java/org/github/_1c_syntax/bsl/languageserver/diagnostics/reporter/AnalysisInfo.java) object

## Sample output

```json
{
    "date": "2019-01-21 01:29:27",
    "fileinfos": [
        {
            "diagnostics": [
                {
                    "code": "FunctionShouldHaveReturnDiagnostic",
                    "message": "Функция не содержит \"Возврат\"",
                    "range": {
                        "end": {
                            "character": 29,
                            "line": 43
                        },
                        "start": {
                            "character": 8,
                            "line": 43
                        }
                    },
                    "relatedInformation": null,
                    "severity": "Error",
                    "source": "bsl-language-server"
                }
            ]
        }
    ]
}
```
