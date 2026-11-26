# Style element constructor (StyleElementConstructors)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description
You should use style elements to change the appearance, rather than setting specific values directly in the controls. This is required in order for similar controls to look the same in all forms where they occur.

Types of style elements:

* `Color` - RGB value is set
* `Font` - type, size and style are set
* `Border` - the type and width of the borders are set

The parameterless constructors `New Font()` and `New Color()` select the default appearance and are not reported. This also applies to Russian names, omitted parentheses and `New("Font")` / `New("Color")`. Constructors with supplied arguments are still checked.

## Examples
<!-- В данном разделе приводятся примеры, на которые диагностика срабатывает, а также можно привести пример, как можно исправить ситуацию -->

## Sources
System of standards
* Source: [Standard: Style Elements (RU)](https://its.1c.ru/db/v8std#content:667:hdoc)
