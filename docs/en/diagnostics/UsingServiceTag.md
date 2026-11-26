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

The `serviceTags` parameter replaces the entire default regular expression.
To keep the default tags (including tags added in future versions), leave it unchanged:

* `additionalServiceTags` — a regular expression for additional tags. Empty by default (no additions).
* `excludedServiceTags` — a regular expression for exclusions. Empty by default (no exclusions).

Empty and whitespace-only values disable the respective additions or exclusions.
Exclusions apply to each matched tag and take precedence over both tag sets.
An excluded tag does not hide another tag in the same comment: for example, with `@` excluded,
`// @strict-types // TODO: check` still produces a diagnostic for `TODO`.
Each comment produces at most one diagnostic: a non-excluded tag from `serviceTags` takes priority over additional tags.

Expressions match the text after `//` and optional whitespace, case-insensitively, including Cyrillic letters.
Spaces in expressions are ignored; use `\s` for whitespace and `\#` for a literal `#`.
Word boundaries are not added automatically: `todo` also matches `todos`; use `todo\b` to match a whole word.
Backslashes must be doubled in JSON: `"todo\\b"`.
For backreferences, the first user capture group has number 2.
Each expression has its own capture group numbering.

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
