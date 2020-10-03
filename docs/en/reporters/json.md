# JSON reporter

Reporter option - `json`

## Description

Output the analize result to file  `bsl-json.json` in the current workspace directory. Output the result of JSON serialization [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/reporters/data/AnalysisInfo.java) object

## Sample output

```json
{
    "date": "2019-01-21 01:29:27",
    "fileinfos": [
        {
            "path": "file:///tmp/src/Module.bsl",
            "mdoRef": "",
            "diagnostics": [
                {
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
                    "severity": "Error",
                    "code": "FunctionShouldHaveReturnDiagnostic",
                    "source": "bsl-language-server",
                    "message": "Функция не содержит \"Возврат\"",
                    "tags": null,
                    "relatedInformation": null
                }
            ],
            "metrics": {
                "procedures": 1,
                "functions": 1,
                "lines": 10,
                "ncloc": 9,
                "comments": 1,
                "statements": 60,
                "nclocData": [
                    1,
                    2,
                    3,
                    4,
                    5,
                    6,
                    7,
                    8,
                    10
                ],
                "covlocData": [
                    2,
                    3,
                    4,
                    5,
                    6,
                    7,
                    8
                ],
                "cognitiveComplexity": 13,
                "cyclomaticComplexity": 17
            }
        }
    ],
    "sourceDir": "file:///tmp/src"
}
```
