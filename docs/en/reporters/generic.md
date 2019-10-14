# Generic Issue reporter

Reporter option - `generic`

## Description

Output the analize result to file `bsl-generic-json.json` in the current workspace directory. See more [Generic Issue](https://docs.sonarqube.org/latest/analysis/generic-issue/).

## Sample output

```

{
    "issues": [
        {
            "engineId": "bsl-language-server",
            "ruleId": "FunctionShouldHaveReturn",
            "severity": "CRITICAL",
            "type": "BUG",
            "primaryLocation": {
                "message": "Функция не содержит \"Возврат\"",
                "filePath": "project/src/DataProcessors/ОбменДанными/Ext/ObjectModule.bsl",
                "textRange": {
                    "startLine": 66,
                    "endLine": 66,
                    "startColumn": 9,
                    "endColumn": 31
                }
            },
            "effortMinutes": 0
        }
    ]
}
```
