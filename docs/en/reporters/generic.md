# Generic Issue reporter

Reporter option - `generic`

## Description

Output the analize result to file `bsl-generic-json.json` in the current workspace directory. See more [Generic Issue](https://docs.sonarqube.org/latest/analysis/generic-issue/).

## Sample output

```json
{
    "issues": [
        {
            "engineId": "bsl-language-server",
            "ruleId": "FunctionShouldHaveReturn",
            "severity": "CRITICAL",
            "type": "BUG",
            "primaryLocation": {
                "message": "Function should have \"Return\"",
                "filePath": "project/src/DataProcessors/Exchange/Ext/ObjectModule.bsl",
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
