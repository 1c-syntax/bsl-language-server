# Generic Issue reporter

Ключ репортера - `generic`

## Описание

Выводит результаты анализа в файл `bsl-generic-json.json` в текущей рабочей директории. Более подробнее о формате [Generic Issue](https://docs.sonarqube.org/latest/analysis/generic-issue/).

## Пример вывода

```json
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
