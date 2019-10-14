# JSON reporter

Ключ репортера - `json`

## Описание

Выводит результаты анализа в файл `bsl-json.json` в текущей рабочей директории. Выводится результат работы метода сериализиатора JSON для объекта [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/master/src/main/java/org/github/_1c_syntax/bsl/languageserver/diagnostics/reporter/AnalysisInfo.java)

## Пример вывода

```json
{
    "date": "2019-01-21 01:29:27",
    "sourceDir": "file:///tmp/src",
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
