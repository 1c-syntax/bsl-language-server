# JSON reporter

Reporter option - `json`

## Description

Output the analize result to file  `bsl-json.json` in the current workspace directory. Output the result of JSON serialization [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/reporters/data/AnalysisInfo.java) object

## Scheme:

- *date* - date of analysis.
- *fileinfos* - array of file information.
- *sourceDir* - path to the source directory without the "file: ///" prefix.

- ### fileinfo:
  - *path* - path to the source file with "file:///" prefix.
  - *mdoRef* - object reference description (empty value ("") is allowed). For example: "Catalog.Organizations"
  - *diagnostics* - array of diagnostics information.
  - *metrics* - file metrics (optional).

  - #### diagnostic:
    - *range* - Location of the error in the file.
    - *severity* - One of Error, Warning, Hint, Information
    - *code* - Diagnostic code.
    - *source* - Diagnostics repo.
    - *message* - Diagnostics message.
    - *tags* - Diagnostics tags. (empty value allowed (null))
    - *relatedInformation* - Array of the location of similar errors in the file or some additional information. (empty value allowed (null)).

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
                    "message": "Function should have \"Return\"",
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
