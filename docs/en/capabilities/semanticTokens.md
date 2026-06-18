# Semantic highlighting

Accurate highlighting based on code analysis: distinguishes variables, parameters, methods and annotations.

**Shortcut:** `automatic`

[← All features](index.md)

## Semantic highlighting

The cursor sits in the open `demo.bsl` module while the editor shows the code as usual. Semantic highlighting colors elements based on parsing: variables, parameters, method names and annotations each get distinct colors.

![semanticTokens-01](https://github.com/user-attachments/assets/8414c5d2-12e0-46bf-9278-25c82fe9a70c)

## Query language (SDBL) semantic highlighting

In the `query.bsl` module the cursor moves to a string literal that holds a query text. The embedded query language (SDBL) is highlighted right inside the string: keywords (`ВЫБРАТЬ`, `ИЗ`, `ГДЕ`), tables and fields are colored like real code.

![semanticTokens-02-query](https://github.com/user-attachments/assets/d5607278-54de-4bdc-9c67-58acb4c5694d)

## BSLDoc comment semantic highlighting

The cursor sits in the doc-comment block above the `РассчитатьСкидку` function. Semantic highlighting colors the BSLDoc markup: the `Параметры`/`Возвращаемое значение` sections, parameter names, types (`Структура`, `Число`, `СправочникСсылка.Клиенты`) and descriptions are each shown in distinct colors.

![semanticTokens-03-bsldoc](https://github.com/user-attachments/assets/166387e9-7790-41db-91d6-3bd62c0f7515)
