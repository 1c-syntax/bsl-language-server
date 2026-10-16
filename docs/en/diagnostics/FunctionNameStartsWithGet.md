# Function name shouldn't start with "Получить" (FunctionNameStartsWithGet)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The word `Получить` is redundant in a function name because a function returns a value by definition.

## Examples

The `prefixes` parameter accepts a comma-separated list, for example `"АЯ_, БСП_"`.
The rule checks both the original name and the name following any one configured prefix, case-insensitively.
Prefixes are literal text, not regular expressions. Surrounding whitespace and empty entries are ignored.
The default empty value checks only the original name. Prefixes are not stripped repeatedly.

With `"prefixes": "АЯ_"`, `АЯ_ПолучитьВажныеДанные()` is also reported.
The rule checks the Russian word `Получить`; it does not check English `Get`.

```bsl
// Incorrect: 
Function ПолучитьИмяПоКоду()

// Correct: 
Function ИмяПоКоду()
```


## Sources
* Source: [Standard: Names of procedures and functions c 6.1 (RU)](https://its.1c.ru/db/v8std#content:647:hdoc)
