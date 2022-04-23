# Invalid character (InvalidCharacterInFile)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

In the texts of modules (including comments) it is not allowed to use inextricable spaces and the minus sign "-" in other encodings (short, long dash, soft hyphen, etc.).

Such characters often appear in the text of the modules when copying from office documents and lead to a number of difficulties in the development.

Example:

- the search for fragments of text that includes “wrong” minuses and spaces does not work
- hints of types of parameters of procedures and functions in the configurator and extended verification in 1C: EDT are incorrectly displayed
- specifying a “wrong” minus in expressions will result in a syntax error

Diagnostics detects the following invalid characters

- En Dash
- Figure Dash
- Em Dash
- Horizontal Bar
- "Wrong" Minus
- Soft Hyphen
- Non-breaking Space

## Sources

* [Standard: Modules texts(RU)](https://its.1c.ru/db/v8std#content:456:hdoc)
