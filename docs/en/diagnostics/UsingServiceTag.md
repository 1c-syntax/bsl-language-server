# Using service tags (UsingServiceTag)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

The diagnostic finds use of service tags in comments. Tags list:

* TODO
* FIXME
* !!
* @
* MRG
* ОТЛАДКА
* ДЛЯ ОТЛАДКИ
* КОНСТРУКТОР_ЗАПРОСА_С_ОБРАБОТКОЙ_РЕЗУЛЬТАТА
* КОНСТРУКТОР_ДВИЖЕНИЙ_РЕГИСТРОВ
* КОНСТРУКТОР_ПЕЧАТИ
* КОНСТРУКТОР_ВВОДА_НА_ОСНОВАНИИ
* Вставить содержимое обработчика
* Insert handler code
* Insert handler contents
* Paste handler content

The `serviceTags` parameter still replaces the entire default regular expression.
To keep the default tags (including tags added in future versions), leave it unchanged:

* `additionalServiceTags` — a regular expression for additional tags. Empty by default (no additions).
* `excludedServiceTags` — a regular expression for exclusions. Empty by default (no exclusions).

Exclusions take precedence over both `serviceTags` and `additionalServiceTags`: a comment matching an exclusion produces no diagnostic.
All expressions match the start of the text after `//` and optional whitespace, using the same Java Pattern flags as `serviceTags`: `MULTILINE`, `CASE_INSENSITIVE`, `COMMENTS`.
Spaces in expressions are ignored; use `\s` for whitespace and `\#` for a literal `#`.
Word boundaries are not added automatically: `todo` also matches `todos`; use `todo\b` to match a whole word.
Backslashes must be doubled in JSON: `"todo\\b"`.
Each expression is compiled independently; as with `serviceTags`, user capture groups start at number 2.
When both the base and additional patterns match, one diagnostic is reported using the base pattern's matched text.

For example, exclude EDT annotations starting with `@` and add the `REVIEW` tag while keeping the other default tags:

```json
{
  "diagnostics": {
    "parameters": {
      "UsingServiceTag": {
        "excludedServiceTags": "@",
        "additionalServiceTags": "review\\b"
      }
    }
  }
}
```

These parameters also apply when `serviceTags` is customized.
