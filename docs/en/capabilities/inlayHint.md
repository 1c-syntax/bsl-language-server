# Inlay hints

Inline hints embedded in the code — for example, parameter names at call sites.

**Shortcut:** `automatic`

[← All features](index.md)

## Inlay hints (parameter names)

In `demo.bsl` the cursor moves to a line with a method call passing positional arguments. Right before each argument the editor shows an inline hint with the name of the matching parameter, without changing the code text itself.

![inlayHint-01](https://github.com/user-attachments/assets/8f29a865-66ed-43fd-80a7-599f4506485d)

## Inlay hints (inferred variable types)

In the `ДемоТипов` procedure variables are declared via assignments (`Колонки = Таблица.Колонки`, `ВсегоСтрок = Таблица.Количество()`). After each variable name the editor shows an inline hint with the inferred type: `Колонки: КоллекцияКолонокТаблицыЗначений`, `ВсегоСтрок: Число`.

![inlayHint-02-types](https://github.com/user-attachments/assets/f4b82f27-822b-4729-872a-21f116edfa83)
