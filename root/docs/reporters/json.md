# JSON reporter

Ключ репортера - `json`

## Описание

Выводит результаты анализа в файл `bsl-json.json` в текущей рабочей директории. Выводится результат работы метода сериализатора JSON для объекта [AnalysisInfo](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/reporters/data/AnalysisInfo.java)

## Схема:

- *date* - дата анализа.
- *fileinfos* - массив описаний файлов.
- *sourceDir* - Путь к папке с исходниками без префикса "file:///" с разделителями по умолчанию для ОС.

- ### fileinfo:
  - *path* - Полный путь к файлу с ошибками, с описанием схемы ("file:///") разделителем должен быть "/".
  - *mdoRef* - Описание ссылки на объект (допускается пустое значение ("") ) пример: "Catalog.Организации"
  - *diagnostics* - массив описаний диагностических сообщений.
  - *metrics* - метрики файла (не обязательный).

  - #### diagnostic:
    - *range* - Описание местоположения ошибки в файле.
    - *severity* - Одно из Error, Warning, Hint, Information
    - *code* - Код правила ошибки.
    - *source* - Репозиторий правила.
    - *message* - Сообщение ошибки.
    - *tags* - Тэги, к которым будет отнесена ошибка. (допускается пустое значение (null))
    - *relatedInformation* - Массив (допускается пустое значение (null) ) дополнительные места срабатывания ошибки, дополнительная уточняющая информация.

## Пример вывода

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
                    "relatedInformation":  [
                        {
                            "location": {
                                "uri": "file:///tmp/src/Module.bsl",
                                "range": {
                                    "end": {
                                        "character": 29,
                                        "line": 43
                                    },
                                    "start": {
                                        "character": 8,
                                        "line": 43
                                    }
                                }
                            },
                            "message": "+1"
                        },
                        {
                            "location": {
                                "uri": "file:///tmp/src/Module.bsl",
                                "range": {
                                    "end": {
                                        "character": 29,
                                        "line": 100
                                    },
                                    "start": {
                                        "character": 8,
                                        "line": 100
                                    }
                                }
                            },
                            "message": "+1"
                        }
                    ]
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
    "sourceDir": "/tmp/src"
}
```
