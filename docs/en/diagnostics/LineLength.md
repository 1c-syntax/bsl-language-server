# Line Length limit (LineLength)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

If the line length is grater than 120 characters you should you line break. It is not recommended to have lines longer than 120 characters, except when line break is impossible (example, in code we have a string constant which is displayed without line breaks in message window using object MessageToUser).

## Parameters

### checkComments

Type: `Boolean`  
Default value: `True`

Check the line length of comments (regular comments, method descriptions and trailing comments).

When set to `False`, all comments are excluded from the check, including method descriptions and trailing comments (at the end of a line with code). In this case the `checkMethodDescription` and `excludeTrailingComments` parameters do not affect the result — they are only relevant when `checkComments = True`. A long code part of the line is still diagnosed: the diagnostic range ends at the code, not at the excluded comment.

Configuration example:

```json
{
  "LineLength": {
    "checkComments": false
  }
}
```

## Sources

* Source: [Standard: Modules (RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
