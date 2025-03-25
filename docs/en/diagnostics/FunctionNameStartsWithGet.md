# Function name shouldn't start with "Получить" (FunctionNameStartsWithGet)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In the name of the function, the word get superfluous since function by definition returns a value.

## Examples
```bsl
// Incorrect: 
Function GetNameByCode()

// Correct: 
Function NameByCode()
```


## Sources
* Source: [Standard: Names of procedures and functions c 6.1 (RU)](https://its.1c.ru/db/v8std#content:647:hdoc)
